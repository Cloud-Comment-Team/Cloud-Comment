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
    Instant createdAt
) {
}
