package com.cloudcomment.comment.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.comment.api.validation.ValidPageUrl;
import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.PublicCommentService;
import com.cloudcomment.comment.application.PublicWidgetConfig;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.shared.web.PaginatedResponse;
import com.cloudcomment.shared.web.security.CurrentUser;
import com.cloudcomment.shared.web.security.BearerTokenResolver;
import com.cloudcomment.shared.web.security.PublicApi;
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
import org.springframework.web.bind.annotation.PathVariable;
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
        @RequestParam(defaultValue = "1") @Min(1) @Max(100_000) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        String origin = requestOriginResolver.resolve(request);
        CommentPage comments = publicCommentService.listComments(
            siteId,
            origin,
            pageUrl,
            page,
            pageSize,
            resolveOptionalViewer(request)
        );
        return PaginatedResponse.of(
            comments.items().stream().map(CommentResponse::from).toList(),
            comments.page(),
            comments.pageSize(),
            comments.totalItems()
        );
    }

    @PostMapping("/pages/comments")
    ResponseEntity<CommentResponse> createComment(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        HttpServletRequest request,
        @Valid @RequestBody CreateCommentRequest body
    ) {
        CommentView comment = publicCommentService.createComment(
            currentUser,
            siteId,
            requestOriginResolver.resolve(request),
            body.pageUrl(),
            body.parentId(),
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
        return CommentReactionsResponse.from(publicCommentService.setReaction(
            currentUser,
            siteId,
            requestOriginResolver.resolve(request),
            commentId,
            body.type()
        ));
    }

    private Optional<UUID> resolveOptionalViewer(HttpServletRequest request) {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(currentUserService.getCurrentUser(bearerTokenResolver.resolve(request)).id());
        } catch (ApplicationException exception) {
            return Optional.empty();
        }
    }
}
