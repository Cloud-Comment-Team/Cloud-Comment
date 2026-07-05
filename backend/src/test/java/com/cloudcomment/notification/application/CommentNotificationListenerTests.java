package com.cloudcomment.notification.application;

import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentCreatedEvent;
import com.cloudcomment.notification.domain.NewCommentNotification;
import com.cloudcomment.notification.domain.NotificationTarget;
import com.cloudcomment.notification.persistence.NotificationTargetRepository;
import com.cloudcomment.realtime.application.RealtimeMessagingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentNotificationListenerTests {

    private static final Instant CREATED_AT = Instant.parse("2026-07-05T08:00:00Z");

    @Test
    void sendsNewCommentNotificationToSiteOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        NotificationTargetRepository repository = mock(NotificationTargetRepository.class);
        RealtimeMessagingService realtimeMessagingService = mock(RealtimeMessagingService.class);
        when(repository.findCommentTarget(siteId, pageId))
            .thenReturn(Optional.of(new NotificationTarget(ownerId, "Demo site", "https://example.com/page")));
        CommentNotificationListener listener = new CommentNotificationListener(repository, realtimeMessagingService);

        listener.onCommentCreated(new CommentCreatedEvent(
            siteId,
            pageId,
            commentId,
            null,
            "visitor@example.com",
            "A new comment that should be delivered to the site owner",
            CommentStatus.PENDING,
            CREATED_AT
        ));

        var payloadCaptor = forClass(Object.class);
        verify(realtimeMessagingService).sendToUser(
            org.mockito.ArgumentMatchers.eq(ownerId),
            org.mockito.ArgumentMatchers.eq("comment.created"),
            payloadCaptor.capture()
        );
        NewCommentNotification payload = (NewCommentNotification) payloadCaptor.getValue();
        assertThat(payload.commentId()).isEqualTo(commentId);
        assertThat(payload.siteId()).isEqualTo(siteId);
        assertThat(payload.siteName()).isEqualTo("Demo site");
        assertThat(payload.pageUrl()).isEqualTo("https://example.com/page");
        assertThat(payload.authorEmail()).isEqualTo("visitor@example.com");
        assertThat(payload.status()).isEqualTo(CommentStatus.PENDING);
        assertThat(payload.contentPreview()).isEqualTo("A new comment that should be delivered to the site owner");
    }

    @Test
    void skipsNotificationWhenTargetIsMissing() {
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        NotificationTargetRepository repository = mock(NotificationTargetRepository.class);
        RealtimeMessagingService realtimeMessagingService = mock(RealtimeMessagingService.class);
        when(repository.findCommentTarget(siteId, pageId)).thenReturn(Optional.empty());
        CommentNotificationListener listener = new CommentNotificationListener(repository, realtimeMessagingService);

        listener.onCommentCreated(new CommentCreatedEvent(
            siteId,
            pageId,
            UUID.randomUUID(),
            null,
            "visitor@example.com",
            "Hello",
            CommentStatus.APPROVED,
            CREATED_AT
        ));

        verify(realtimeMessagingService, never()).sendToUser(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }
}
