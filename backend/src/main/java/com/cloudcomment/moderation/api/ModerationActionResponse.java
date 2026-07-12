package com.cloudcomment.moderation.api;

import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.ModerationActionType;
import com.cloudcomment.moderation.domain.CommentStatus;

import java.time.Instant;
import java.util.UUID;

public record ModerationActionResponse(
    UUID id,
    UUID commentId,
    ModerationActionType action,
    CommentStatus fromStatus,
    CommentStatus toStatus,
    String reason,
    PerformerResponse performedBy,
    UUID operationId,
    UUID revertsActionId,
    Instant createdAt
) {

    static ModerationActionResponse from(ModerationAction moderationAction) {
        return new ModerationActionResponse(
            moderationAction.id(),
            moderationAction.commentId(),
            moderationAction.action(),
            moderationAction.fromStatus(),
            moderationAction.toStatus(),
            moderationAction.reason(),
            new PerformerResponse(moderationAction.moderatorId(), moderationAction.moderatorEmail()),
            moderationAction.operationId(),
            moderationAction.revertsActionId(),
            moderationAction.createdAt()
        );
    }
}
