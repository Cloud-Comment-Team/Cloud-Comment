package com.cloudcomment.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record OwnerNotification(
    UUID id,
    UUID ownerId,
    UUID commentId,
    Instant readAt,
    Instant createdAt
) {
}
