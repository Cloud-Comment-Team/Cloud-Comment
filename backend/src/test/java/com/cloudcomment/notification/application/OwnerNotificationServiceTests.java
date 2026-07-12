package com.cloudcomment.notification.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.notification.domain.OwnerNotification;
import com.cloudcomment.notification.persistence.OwnerNotificationRepository;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerNotificationServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-12T18:00:00Z");

    @Test
    void createsIdempotentCommentNotificationKey() {
        OwnerNotificationRepository repository = mock(OwnerNotificationRepository.class);
        UUID ownerId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        OwnerNotification stored = new OwnerNotification(UUID.randomUUID(), ownerId, commentId, null, NOW);
        when(repository.create(ownerId, commentId, "comment-created:" + commentId, NOW)).thenReturn(stored);
        OwnerNotificationService service = service(repository);

        assertThat(service.createForComment(ownerId, commentId, NOW)).isSameAs(stored);
        verify(repository).create(ownerId, commentId, "comment-created:" + commentId, NOW);
    }

    @Test
    void readsAndChangesOnlyCurrentOwnerNotifications() {
        OwnerNotificationRepository repository = mock(OwnerNotificationRepository.class);
        AuthenticatedUser user = currentUser();
        UUID notificationId = UUID.randomUUID();
        OwnerNotificationService service = service(repository);
        when(repository.markRead(user.id(), notificationId, NOW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(user, notificationId))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");

        service.unreadCount(user);
        service.markAllRead(user);
        verify(repository).countUnreadByOwnerId(user.id());
        verify(repository).markAllRead(user.id(), NOW);
    }

    @Test
    void removesNotificationsOlderThanNinetyDays() {
        OwnerNotificationRepository repository = mock(OwnerNotificationRepository.class);
        OwnerNotificationService service = service(repository);

        service.cleanupExpired();

        verify(repository).deleteCreatedBefore(NOW.minusSeconds(90L * 24 * 60 * 60));
    }

    private OwnerNotificationService service(OwnerNotificationRepository repository) {
        return new OwnerNotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), NOW, NOW);
    }
}
