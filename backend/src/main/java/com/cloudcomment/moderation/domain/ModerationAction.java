package com.cloudcomment.moderation.domain;

import java.time.Instant;
import java.util.UUID;

public record ModerationAction(
    UUID id,
    UUID commentId,
    ModerationActionType action,
    CommentStatus fromStatus,
    CommentStatus toStatus,
    String reason,
    UUID moderatorId,
    String moderatorEmail,
    UUID operationId,
    UUID revertsActionId,
    Instant createdAt
) {
    public ModerationAction(
        UUID id, UUID commentId, ModerationActionType action, CommentStatus fromStatus, CommentStatus toStatus,
        String reason, UUID moderatorId, String moderatorEmail, Instant createdAt
    ) {
        this(id, commentId, action, fromStatus, toStatus, reason, moderatorId, moderatorEmail, null, null, createdAt);
    }
}
