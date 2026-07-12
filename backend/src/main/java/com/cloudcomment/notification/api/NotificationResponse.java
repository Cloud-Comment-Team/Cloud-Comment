package com.cloudcomment.notification.api;

import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.notification.domain.OwnerNotificationView;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID commentId,
    UUID siteId,
    String siteName,
    UUID pageId,
    String pageUrl,
    UUID parentId,
    String authorEmail,
    String contentPreview,
    CommentStatus status,
    Instant readAt,
    Instant createdAt
) {
    private static final int PREVIEW_LIMIT = 180;

    static NotificationResponse from(OwnerNotificationView notification) {
        return new NotificationResponse(
            notification.id(),
            notification.commentId(),
            notification.siteId(),
            notification.siteName(),
            notification.pageId(),
            notification.pageUrl(),
            notification.parentId(),
            notification.authorEmail(),
            preview(notification.content()),
            notification.status(),
            notification.readAt(),
            notification.createdAt()
        );
    }

    private static String preview(String content) {
        if (content == null || content.length() <= PREVIEW_LIMIT) {
            return content;
        }
        return content.substring(0, PREVIEW_LIMIT - 1).stripTrailing() + "...";
    }
}
