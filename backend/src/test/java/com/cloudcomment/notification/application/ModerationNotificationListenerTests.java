package com.cloudcomment.notification.application;

import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationActionAppliedEvent;
import com.cloudcomment.moderation.domain.ModerationActionType;
import com.cloudcomment.notification.domain.ModerationActionNotification;
import com.cloudcomment.realtime.application.RealtimeMessagingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ModerationNotificationListenerTests {

    @Test
    void sendsModerationActionNotificationToCurrentOwnerSession() {
        UUID ownerId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-05T09:00:00Z");
        RealtimeMessagingService realtimeMessagingService = mock(RealtimeMessagingService.class);
        ModerationNotificationListener listener = new ModerationNotificationListener(realtimeMessagingService);

        listener.onModerationActionApplied(new ModerationActionAppliedEvent(
            siteId,
            pageId,
            commentId,
            ModerationActionType.APPROVE,
            CommentStatus.PENDING,
            CommentStatus.APPROVED,
            "Looks good",
            ownerId,
            createdAt
        ));

        var payloadCaptor = forClass(Object.class);
        verify(realtimeMessagingService).sendToUser(
            org.mockito.ArgumentMatchers.eq(ownerId),
            org.mockito.ArgumentMatchers.eq("comment.moderation_action_applied"),
            payloadCaptor.capture()
        );
        ModerationActionNotification payload = (ModerationActionNotification) payloadCaptor.getValue();
        assertThat(payload.siteId()).isEqualTo(siteId);
        assertThat(payload.pageId()).isEqualTo(pageId);
        assertThat(payload.commentId()).isEqualTo(commentId);
        assertThat(payload.action()).isEqualTo(ModerationActionType.APPROVE);
        assertThat(payload.fromStatus()).isEqualTo(CommentStatus.PENDING);
        assertThat(payload.toStatus()).isEqualTo(CommentStatus.APPROVED);
        assertThat(payload.reason()).isEqualTo("Looks good");
        assertThat(payload.moderatorId()).isEqualTo(ownerId);
        assertThat(payload.createdAt()).isEqualTo(createdAt);
    }
}
