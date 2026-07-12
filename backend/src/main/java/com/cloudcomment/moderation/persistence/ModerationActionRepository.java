package com.cloudcomment.moderation.persistence;

import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.ModerationActionType;

import java.util.Optional;
import java.util.UUID;

public interface ModerationActionRepository {

    ModerationAction create(
        UUID commentId,
        UUID moderatorId,
        ModerationActionType action,
        CommentStatus fromStatus,
        CommentStatus toStatus,
        String reason
    );

    default ModerationAction create(
        UUID commentId, UUID moderatorId, ModerationActionType action, CommentStatus fromStatus,
        CommentStatus toStatus, String reason, UUID operationId, UUID revertsActionId
    ) {
        return create(commentId, moderatorId, action, fromStatus, toStatus, reason);
    }

    default Optional<ModerationAction> findById(UUID actionId) {
        return Optional.empty();
    }

    default Optional<ModerationAction> findByCommentIdAndOperationId(UUID commentId, UUID operationId) {
        return Optional.empty();
    }

    default Optional<ModerationAction> findLatestNotReverted(UUID commentId) {
        return Optional.empty();
    }
}
