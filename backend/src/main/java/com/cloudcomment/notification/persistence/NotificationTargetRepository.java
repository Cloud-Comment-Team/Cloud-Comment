package com.cloudcomment.notification.persistence;

import com.cloudcomment.notification.domain.NotificationTarget;

import java.util.Optional;
import java.util.UUID;

public interface NotificationTargetRepository {

    Optional<NotificationTarget> findCommentTarget(UUID siteId, UUID pageId);
}
