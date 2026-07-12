package com.cloudcomment.notification.persistence;

import com.cloudcomment.notification.application.OwnerNotificationPage;
import com.cloudcomment.notification.domain.OwnerNotification;
import com.cloudcomment.notification.domain.OwnerNotificationView;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OwnerNotificationRepository {

    OwnerNotification create(UUID ownerId, UUID commentId, String deduplicationKey, Instant createdAt);

    OwnerNotificationPage findByOwnerId(UUID ownerId, int page, int pageSize);

    long countUnreadByOwnerId(UUID ownerId);

    Optional<OwnerNotificationView> markRead(UUID ownerId, UUID notificationId, Instant readAt);

    int markAllRead(UUID ownerId, Instant readAt);

    int deleteCreatedBefore(Instant threshold);
}
