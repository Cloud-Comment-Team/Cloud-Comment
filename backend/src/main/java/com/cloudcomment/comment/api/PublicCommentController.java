package com.cloudcomment.comment.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.comment.api.validation.ValidPageUrl;
import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.PublicCommentService;
import com.cloudcomment.comment.application.PublicWidgetConfig;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.domain.PublicCommentSort;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.shared.web.PaginatedResponse;
import com.cloudcomment.shared.web.security.CurrentUser;
import com.cloudcomment.shared.web.security.BearerTokenResolver;
import com.cloudcomment.shared.web.security.PublicApi;
import com.cloudcomment.shared.web.security.WidgetRequestContext;
import com.cloudcomment.widgetcontext.application.WidgetContextService;
import com.cloudcomment.widgetcontext.application.WidgetClientIpResolver;
import com.cloudcomment.widgetcontext.application.WidgetSecurityRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/public/sites/{siteId}")
@RequiredArgsConstructor
class PublicCommentController {

    private final PublicCommentService publicCommentService;
    private final WidgetRequestOriginResolver requestOriginResolver;
    private final BearerTokenResolver bearerTokenResolver;
    private final CurrentUserService currentUserService;
    private final WidgetContextService widgetContextService;
    private final WidgetSecurityRateLimiter rateLimiter;
    private final WidgetClientIpResolver clientIpResolver;

    @PublicApi
    @GetMapping("/config")
    PublicWidgetConfigResponse getConfig(
        @PathVariable UUID siteId,
        HttpServletRequest request
    ) {
        String origin = requestOriginResolver.resolve(request);
        PublicWidgetConfig config = publicCommentService.getConfig(siteId, origin);
        return PublicWidgetConfigResponse.from(config);
    }

    @PublicApi
    @GetMapping("/pages/comments")
    PaginatedResponse<CommentResponse> listComments(
        @PathVariable UUID siteId,
        HttpServletRequest request,
        @RequestParam @NotBlank @Size(max = 2048) @ValidPageUrl String pageUrl,
        @RequestParam(defaultValue = "PINNED_FIRST") PublicCommentSort sort,
        @RequestParam(defaultValue = "1") @Min(1) @Max(100_000) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) @Min(0) @Max(100) Integer replyLimit
    ) {
        requireMatchingContextPage(request, pageUrl);
        String origin = requestOriginResolver.resolve(request);
        CommentPage comments = publicCommentService.listComments(
            siteId,
            origin,
            pageUrl,
            page,
            pageSize,
            sort,
            resolveOptionalViewer(request),
            replyLimit
        );
        return PaginatedResponse.of(
            comments.items().stream().map(CommentResponse::from).toList(),
            comments.page(),
            comments.pageSize(),
            comments.totalItems()
        );
    }

    @PublicApi
    @GetMapping("/comments/{commentId}/replies")
    PaginatedResponse<CommentResponse> listReplies(
        @PathVariable UUID siteId,
        @PathVariable UUID commentId,
        HttpServletRequest request,
        @RequestParam(defaultValue = "1") @Min(1) @Max(100_000) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        requireCommentBoundToContextPage(request, siteId, commentId);
        CommentPage replies = publicCommentService.listReplies(
            siteId,
            requestOriginResolver.resolve(request),
            commentId,
            page,
            pageSize,
            resolveOptionalViewer(request)
        );
        return PaginatedResponse.of(
            replies.items().stream().map(CommentResponse::from).toList(),
            replies.page(),
            replies.pageSize(),
            replies.totalItems()
        );
    }

    @PublicApi
    @GetMapping("/comments/{commentId}/permalink")
    CommentPermalinkResponse locateComment(
        @PathVariable UUID siteId,
        @PathVariable UUID commentId,
        HttpServletRequest request,
        @RequestParam @NotBlank @Size(max = 2048) @ValidPageUrl String pageUrl,
        @RequestParam(defaultValue = "PINNED_FIRST") PublicCommentSort sort,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        requireMatchingContextPage(request, pageUrl);
        return CommentPermalinkResponse.from(publicCommentService.locateComment(
            siteId,
            requestOriginResolver.resolve(request),
            pageUrl,
            commentId,
            pageSize,
            sort,
            resolveOptionalViewer(request)
        ));
    }

    @PublicApi
    @PostMapping("/pages/comments")
    ResponseEntity<CommentResponse> createComment(
        @PathVariable UUID siteId,
        HttpServletRequest request,
        @Valid @RequestBody CreateCommentRequest body
    ) {
        requireMatchingContextPage(request, body.pageUrl());
        String origin = requestOriginResolver.resolve(request);
        Optional<AuthenticatedUser> currentUser = resolveOptionalCurrentUser(request);
        rateLimiter.checkComment(siteId, origin, clientIpResolver.resolve(request));
        CommentView comment = publicCommentService.createComment(
            currentUser,
            siteId,
            origin,
            body.pageUrl(),
            body.parentId(),
            body.guestName(),
            body.content()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment));
    }

    @PutMapping("/comments/{commentId}/reaction")
    CommentReactionsResponse setReaction(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @PathVariable UUID commentId,
        HttpServletRequest request,
        @RequestBody CommentReactionRequest body
    ) {
        requireCommentBoundToContextPage(request, siteId, commentId);
        return CommentReactionsResponse.from(publicCommentService.setReaction(
            currentUser,
            siteId,
            requestOriginResolver.resolve(request),
            commentId,
            body.type()
        ));
    }

    @PatchMapping("/comments/{commentId}")
    CommentResponse updateOwnComment(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @PathVariable UUID commentId,
        HttpServletRequest request,
        @Valid @RequestBody UpdateCommentRequest body
    ) {
        requireCommentBoundToContextPage(request, siteId, commentId);
        return CommentResponse.from(publicCommentService.updateOwnComment(
            currentUser,
            siteId,
            requestOriginResolver.resolve(request),
            commentId,
            body.content()
        ));
    }

    @DeleteMapping("/comments/{commentId}")
    ResponseEntity<Void> deleteOwnComment(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @PathVariable UUID commentId,
        HttpServletRequest request
    ) {
        requireCommentBoundToContextPage(request, siteId, commentId);
        publicCommentService.deleteOwnComment(
            currentUser,
            siteId,
            requestOriginResolver.resolve(request),
            commentId
        );
        return ResponseEntity.noContent().build();
    }

    private Optional<UUID> resolveOptionalViewer(HttpServletRequest request) {
        return resolveOptionalCurrentUser(request).map(AuthenticatedUser::id);
    }

    private Optional<AuthenticatedUser> resolveOptionalCurrentUser(HttpServletRequest request) {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            return Optional.empty();
        }
        WidgetRequestContext context = WidgetRequestContext.require(request);
        return Optional.of(currentUserService.getWidgetCurrentUser(
            bearerTokenResolver.resolve(request),
            context.siteId(),
            context.origin()
        ));
    }

    private void requireMatchingContextPage(HttpServletRequest request, String pageUrl) {
        WidgetRequestContext.resolve(request).ifPresent(context -> {
            if (!widgetContextService.matchesPageHash(context.pageUrlHash(), pageUrl)) {
                throw new ApplicationException(
                    ApiErrorCode.INVALID_WIDGET_CONTEXT,
                    "Invalid widget context"
                );
            }
        });
    }

    private void requireCommentBoundToContextPage(
        HttpServletRequest request,
        UUID siteId,
        UUID commentId
    ) {
        WidgetRequestContext.resolve(request).ifPresent(context ->
            publicCommentService.assertCommentBelongsToContextPage(
                siteId,
                commentId,
                context.canonicalPageUrl()
            )
        );
    }
}
