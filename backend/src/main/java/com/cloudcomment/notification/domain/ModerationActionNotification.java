package com.cloudcomment.notification.domain;

import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationActionType;

import java.time.Instant;
import java.util.UUID;

public record ModerationActionNotification(
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
