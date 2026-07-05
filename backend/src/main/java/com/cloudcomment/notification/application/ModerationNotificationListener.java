package com.cloudcomment.notification.application;

import com.cloudcomment.moderation.domain.ModerationActionAppliedEvent;
import com.cloudcomment.notification.domain.ModerationActionNotification;
import com.cloudcomment.realtime.application.RealtimeMessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class ModerationNotificationListener {

    static final String MODERATION_ACTION_APPLIED_TYPE = "comment.moderation_action_applied";

    private final RealtimeMessagingService realtimeMessagingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onModerationActionApplied(ModerationActionAppliedEvent event) {
        realtimeMessagingService.sendToUser(
            event.moderatorId(),
            MODERATION_ACTION_APPLIED_TYPE,
            new ModerationActionNotification(
                event.siteId(),
                event.pageId(),
                event.commentId(),
                event.action(),
                event.fromStatus(),
                event.toStatus(),
                event.reason(),
                event.moderatorId(),
                event.createdAt()
            )
        );
    }
}
