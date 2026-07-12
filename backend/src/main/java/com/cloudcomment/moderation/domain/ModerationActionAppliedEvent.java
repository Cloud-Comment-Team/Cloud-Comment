package com.cloudcomment.moderation.domain;

import java.time.Instant;
import java.util.UUID;

public record ModerationActionAppliedEvent(
    UUID siteId,
    UUID pageId,
    UUID commentId,
    ModerationActionType action,
    CommentStatus fromStatus,
    CommentStatus toStatus,
    String reason,
    UUID moderatorId,
    Instant createdAt
) {
}
