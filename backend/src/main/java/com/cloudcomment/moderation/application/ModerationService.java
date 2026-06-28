package com.cloudcomment.moderation.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.ModerationActionType;
import com.cloudcomment.moderation.persistence.CommentRepository;
import com.cloudcomment.moderation.persistence.ModerationActionRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private final CommentRepository commentRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ResourceOwnershipService resourceOwnershipService;

    @Transactional(readOnly = true)
    public ModerationCommentPage listComments(
        AuthenticatedUser currentUser,
        ModerationCommentFilters filters,
        int page,
        int pageSize
    ) {
        ModerationCommentFilters normalizedFilters = normalizeFilters(filters);
        assertFilterOwnership(currentUser, normalizedFilters);
        return commentRepository.findByOwnerId(currentUser.id(), normalizedFilters, page, pageSize);
    }

    @Transactional(readOnly = true)
    public Comment getComment(AuthenticatedUser currentUser, UUID commentId) {
        resourceOwnershipService.assertCommentOwnedBy(currentUser, commentId);
        return commentRepository.findById(commentId).orElseThrow(this::notFound);
    }

    @Transactional
    public ModerationAction applyAction(
        AuthenticatedUser currentUser,
        UUID commentId,
        ModerationActionType action,
        String reason
    ) {
        resourceOwnershipService.assertCommentOwnedBy(currentUser, commentId);
        Comment comment = commentRepository.findById(commentId).orElseThrow(this::notFound);

        String normalizedReason = normalizeReason(reason);
        CommentStatus targetStatus = resolveTargetStatus(action);
        if (comment.status() == targetStatus) {
            throw alreadyHasStatus(targetStatus);
        }

        commentRepository.updateStatus(commentId, comment.status(), targetStatus, normalizedReason)
            .orElseThrow(() -> concurrentStatusChange(commentId, targetStatus));

        return moderationActionRepository.create(
            commentId,
            currentUser.id(),
            action,
            comment.status(),
            targetStatus,
            normalizedReason
        );
    }

    private ModerationCommentFilters normalizeFilters(ModerationCommentFilters filters) {
        if (filters.createdFrom() != null
            && filters.createdTo() != null
            && filters.createdFrom().isAfter(filters.createdTo())) {
            throw new ApplicationException(
                ApiErrorCode.BAD_REQUEST,
                "createdFrom must be before or equal to createdTo"
            );
        }
        return new ModerationCommentFilters(
            filters.siteId(),
            filters.pageId(),
            normalizeNullable(filters.pageUrl()),
            filters.status(),
            filters.createdFrom(),
            filters.createdTo(),
            normalizeNullable(filters.search()),
            filters.sortBy(),
            filters.sortOrder()
        );
    }

    private void assertFilterOwnership(AuthenticatedUser currentUser, ModerationCommentFilters filters) {
        if (filters.siteId() != null) {
            resourceOwnershipService.assertSiteOwnedBy(currentUser, filters.siteId());
        }
        if (filters.pageId() != null) {
            resourceOwnershipService.assertPageOwnedBy(currentUser, filters.pageId());
        }
    }

    private CommentStatus resolveTargetStatus(ModerationActionType action) {
        return switch (action) {
            case APPROVE -> CommentStatus.APPROVED;
            case REJECT -> CommentStatus.REJECTED;
            case HIDE -> CommentStatus.HIDDEN;
            case MARK_SPAM -> CommentStatus.SPAM;
            case RESTORE -> CommentStatus.APPROVED;
        };
    }

    private ApplicationException concurrentStatusChange(UUID commentId, CommentStatus targetStatus) {
        Comment currentComment = commentRepository.findById(commentId).orElseThrow(this::notFound);
        if (currentComment.status() == targetStatus) {
            return alreadyHasStatus(targetStatus);
        }
        return new ApplicationException(
            ApiErrorCode.BUSINESS_ERROR,
            "Comment status changed; retry moderation action"
        );
    }

    private ApplicationException alreadyHasStatus(CommentStatus targetStatus) {
        return new ApplicationException(
            ApiErrorCode.BUSINESS_ERROR,
            "Comment already has status " + targetStatus.name()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeReason(String reason) {
        return normalizeNullable(reason);
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }
}
