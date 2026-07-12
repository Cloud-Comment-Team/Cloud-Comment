package com.cloudcomment.moderation.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.ModerationActionAppliedEvent;
import com.cloudcomment.moderation.domain.ModerationActionType;
import com.cloudcomment.moderation.persistence.CommentRepository;
import com.cloudcomment.moderation.persistence.ModerationActionRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private final CommentRepository commentRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ResourceOwnershipService resourceOwnershipService;
    private final ApplicationEventPublisher eventPublisher;

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
    public Comment updateFlags(
        AuthenticatedUser currentUser,
        UUID commentId,
        Boolean pinned,
        Boolean favorite
    ) {
        if (pinned == null && favorite == null) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "At least one flag must be provided");
        }

        resourceOwnershipService.assertCommentOwnedBy(currentUser, commentId);
        Comment comment = commentRepository.findById(commentId).orElseThrow(this::notFound);
        if (Boolean.TRUE.equals(pinned)) {
            assertCanPin(comment);
        }

        return commentRepository.updateFlags(commentId, pinned, favorite).orElseThrow(this::notFound);
    }

    @Transactional
    public ModerationAction applyAction(
        AuthenticatedUser currentUser,
        UUID commentId,
        ModerationActionType action,
        String reason
    ) {
        return applyAction(currentUser, commentId, action, reason, null);
    }

    @Transactional
    public ModerationAction applyAction(
        AuthenticatedUser currentUser, UUID commentId, ModerationActionType action, String reason, UUID operationId
    ) {
        resourceOwnershipService.assertCommentOwnedBy(currentUser, commentId);
        if (operationId != null) {
            var existing = moderationActionRepository.findByCommentIdAndOperationId(commentId, operationId);
            if (existing.isPresent()) {
                return existing.orElseThrow();
            }
        }
        Comment comment = commentRepository.findById(commentId).orElseThrow(this::notFound);

        String normalizedReason = normalizeReason(reason);
        CommentStatus targetStatus = resolveTargetStatus(action);
        if (comment.status() == targetStatus) {
            throw alreadyHasStatus(targetStatus);
        }

        Comment updatedComment = commentRepository.updateStatus(commentId, comment.status(), targetStatus, normalizedReason)
            .orElseThrow(() -> concurrentStatusChange(commentId, targetStatus));

        ModerationAction moderationAction = moderationActionRepository.create(
            commentId,
            currentUser.id(),
            action,
            comment.status(),
            targetStatus,
            normalizedReason,
            operationId,
            null
        );
        eventPublisher.publishEvent(new ModerationActionAppliedEvent(
            updatedComment.siteId(),
            updatedComment.pageId(),
            commentId,
            action,
            comment.status(),
            targetStatus,
            normalizedReason,
            currentUser.id(),
            moderationAction.createdAt()
        ));
        return moderationAction;
    }

    @Transactional(readOnly = true)
    public Map<CommentStatus, Long> counts(AuthenticatedUser currentUser) {
        return commentRepository.countByOwnerId(currentUser.id());
    }

    @Transactional
    public List<BulkModerationResult> applyBulk(
        AuthenticatedUser currentUser, UUID operationId, List<UUID> commentIds,
        ModerationActionType action, String reason
    ) {
        if (action == ModerationActionType.UNDO) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "UNDO uses a dedicated endpoint");
        }
        if (commentIds.stream().distinct().count() != commentIds.size()) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "commentIds must be unique");
        }
        return commentIds.stream().map(commentId -> {
            try {
                return BulkModerationResult.success(commentId, applyAction(currentUser, commentId, action, reason, operationId));
            } catch (ApplicationException exception) {
                return BulkModerationResult.failure(commentId, "ACTION_FAILED", "Не удалось применить действие");
            }
        }).toList();
    }

    @Transactional
    public ModerationAction undo(AuthenticatedUser currentUser, UUID actionId) {
        ModerationAction action = moderationActionRepository.findById(actionId).orElseThrow(this::notFound);
        resourceOwnershipService.assertCommentOwnedBy(currentUser, action.commentId());
        ModerationAction latest = moderationActionRepository.findLatestNotReverted(action.commentId())
            .orElseThrow(() -> new ApplicationException(ApiErrorCode.BUSINESS_ERROR, "Action cannot be undone"));
        if (!latest.id().equals(action.id()) || action.action() == ModerationActionType.UNDO
            || action.createdAt().isBefore(Instant.now().minus(Duration.ofMinutes(15)))) {
            throw new ApplicationException(ApiErrorCode.BUSINESS_ERROR, "Action cannot be undone");
        }
        Comment comment = commentRepository.findById(action.commentId()).orElseThrow(this::notFound);
        if (comment.status() != action.toStatus()) {
            throw new ApplicationException(ApiErrorCode.BUSINESS_ERROR, "Comment status changed after action");
        }
        Comment updated = commentRepository.updateStatus(comment.id(), comment.status(), action.fromStatus(), "Отмена действия")
            .orElseThrow(() -> concurrentStatusChange(comment.id(), action.fromStatus()));
        ModerationAction undo = moderationActionRepository.create(
            comment.id(), currentUser.id(), ModerationActionType.UNDO, comment.status(), action.fromStatus(),
            "Отмена действия", UUID.randomUUID(), action.id()
        );
        eventPublisher.publishEvent(new ModerationActionAppliedEvent(
            updated.siteId(), updated.pageId(), updated.id(), ModerationActionType.UNDO,
            comment.status(), action.fromStatus(), undo.reason(), currentUser.id(), undo.createdAt()
        ));
        return undo;
    }

    private ModerationCommentFilters normalizeFilters(ModerationCommentFilters filters) {
        if (filters.status() != null && filters.statuses() != null && !filters.statuses().isEmpty()) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "status and statuses cannot be combined");
        }
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
            filters.statuses() == null ? null : List.copyOf(filters.statuses()),
            filters.createdFrom(),
            filters.createdTo(),
            normalizeNullable(filters.search()),
            filters.favorite(),
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
            case UNDO -> throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "UNDO uses a dedicated endpoint");
        };
    }

    private void assertCanPin(Comment comment) {
        if (comment.parentId() != null) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "Only root comments can be pinned");
        }
        if (comment.status() != CommentStatus.APPROVED) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "Only approved comments can be pinned");
        }
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
