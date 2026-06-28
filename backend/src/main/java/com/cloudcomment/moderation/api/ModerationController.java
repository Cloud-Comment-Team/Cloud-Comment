package com.cloudcomment.moderation.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.moderation.application.ModerationCommentFilters;
import com.cloudcomment.moderation.application.ModerationCommentPage;
import com.cloudcomment.moderation.application.ModerationService;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentSortField;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.SortOrder;
import com.cloudcomment.shared.web.PaginatedResponse;
import com.cloudcomment.shared.web.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
class ModerationController {

    private final ModerationService moderationService;

    @GetMapping("/comments")
    PaginatedResponse<CommentResponse> listComments(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(required = false) UUID siteId,
        @RequestParam(required = false) UUID pageId,
        @RequestParam(required = false) String pageUrl,
        @RequestParam(required = false) CommentStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "CREATED_AT") CommentSortField sortBy,
        @RequestParam(defaultValue = "DESC") SortOrder sortOrder,
        @RequestParam(defaultValue = "1") @Min(1) @Max(100_000) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        ModerationCommentFilters filters = new ModerationCommentFilters(
            siteId,
            pageId,
            pageUrl,
            status,
            createdFrom,
            createdTo,
            search,
            sortBy,
            sortOrder
        );
        ModerationCommentPage comments = moderationService.listComments(currentUser, filters, page, pageSize);
        return PaginatedResponse.of(
            comments.items().stream().map(CommentResponse::from).toList(),
            comments.page(),
            comments.pageSize(),
            comments.totalItems()
        );
    }

    @GetMapping("/comments/{commentId}")
    CommentResponse getComment(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID commentId
    ) {
        Comment comment = moderationService.getComment(currentUser, commentId);
        return CommentResponse.from(comment);
    }

    @PostMapping("/comments/{commentId}/actions")
    ResponseEntity<ModerationActionResponse> applyAction(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID commentId,
        @Valid @RequestBody ApplyModerationActionRequest request
    ) {
        ModerationAction action = moderationService.applyAction(
            currentUser,
            commentId,
            request.action(),
            request.reason()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ModerationActionResponse.from(action));
    }
}
