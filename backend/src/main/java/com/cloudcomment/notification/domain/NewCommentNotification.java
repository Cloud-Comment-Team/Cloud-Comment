package com.cloudcomment.notification.domain;

import com.cloudcomment.comment.domain.CommentStatus;

import java.time.Instant;
import java.util.UUID;

public record NewCommentNotification(
    UUID notificationId,
    UUID commentId,
    UUID siteId,
    String siteName,
    UUID pageId,
    String pageUrl,
    UUID parentId,
    String authorEmail,
    String contentPreview,
    CommentStatus status,
    Instant createdAt
) {
}
