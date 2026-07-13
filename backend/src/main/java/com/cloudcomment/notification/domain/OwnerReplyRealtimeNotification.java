package com.cloudcomment.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record OwnerReplyRealtimeNotification(
    UUID commentId,
    UUID rootCommentId,
    UUID siteId,
    UUID pageId,
    Instant createdAt
) {
}
