package com.cloudcomment.notification.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.notification.domain.OwnerNotification;
import com.cloudcomment.notification.domain.OwnerNotificationView;
import com.cloudcomment.notification.persistence.OwnerNotificationRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerNotificationService {

    private static final Duration RETENTION = Duration.ofDays(90);
    private static final String COMMENT_CREATED_PREFIX = "comment-created:";

    private final OwnerNotificationRepository repository;
    private final Clock clock;

    @Transactional
    public OwnerNotification createForComment(UUID ownerId, UUID commentId, Instant createdAt) {
        return repository.create(ownerId, commentId, COMMENT_CREATED_PREFIX + commentId, createdAt);
    }

    @Transactional(readOnly = true)
    public OwnerNotificationPage list(AuthenticatedUser currentUser, int page, int pageSize) {
        return repository.findByOwnerId(currentUser.id(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthenticatedUser currentUser) {
        return repository.countUnreadByOwnerId(currentUser.id());
    }

    @Transactional
    public OwnerNotificationView markRead(AuthenticatedUser currentUser, UUID notificationId) {
        return repository.markRead(currentUser.id(), notificationId, clock.instant())
            .orElseThrow(() -> new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));
    }

    @Transactional
    public int markAllRead(AuthenticatedUser currentUser) {
        return repository.markAllRead(currentUser.id(), clock.instant());
    }

    @Scheduled(cron = "${cloud-comment.notifications.retention-cleanup-cron:0 30 3 * * *}")
    public void cleanupExpired() {
        repository.deleteCreatedBefore(clock.instant().minus(RETENTION));
    }
}
