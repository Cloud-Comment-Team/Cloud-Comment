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
        assertFilterOwnership(currentUser, filters);
        return commentRepository.findByOwnerId(currentUser.id(), filters, page, pageSize);
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

        CommentStatus targetStatus = resolveTargetStatus(action);
        if (comment.status() == targetStatus) {
            throw new ApplicationException(
                ApiErrorCode.BUSINESS_ERROR,
                "Comment already has status " + targetStatus.name()
            );
        }

        commentRepository.updateStatus(commentId, targetStatus, normalizeReason(reason))
            .orElseThrow(this::notFound);

        return moderationActionRepository.create(
            commentId,
            currentUser.id(),
            action,
            comment.status(),
            targetStatus,
            normalizeReason(reason)
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

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }
}
