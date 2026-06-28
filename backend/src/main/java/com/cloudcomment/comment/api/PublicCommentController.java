package com.cloudcomment.comment.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.comment.api.validation.ValidPageUrl;
import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.PublicCommentService;
import com.cloudcomment.comment.application.PublicWidgetConfig;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.shared.web.PaginatedResponse;
import com.cloudcomment.shared.web.security.CurrentUser;
import com.cloudcomment.shared.web.security.PublicApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/public/sites/{siteId}")
@RequiredArgsConstructor
class PublicCommentController {

    private final PublicCommentService publicCommentService;

    @PublicApi
    @GetMapping("/config")
    PublicWidgetConfigResponse getConfig(
        @PathVariable UUID siteId,
        @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin
    ) {
        PublicWidgetConfig config = publicCommentService.getConfig(siteId, origin);
        return PublicWidgetConfigResponse.from(config);
    }

    @PublicApi
    @GetMapping("/pages/comments")
    PaginatedResponse<CommentResponse> listComments(
        @PathVariable UUID siteId,
        @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
        @RequestParam @NotBlank @Size(max = 2048) @ValidPageUrl String pageUrl,
        @RequestParam(defaultValue = "1") @Min(1) @Max(100_000) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        CommentPage comments = publicCommentService.listComments(siteId, origin, pageUrl, page, pageSize);
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
        @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
        @Valid @RequestBody CreateCommentRequest request
    ) {
        CommentView comment = publicCommentService.createComment(
            currentUser,
            siteId,
            origin,
            request.pageUrl(),
            request.parentId(),
            request.content()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment));
    }
}
