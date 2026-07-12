package com.cloudcomment.notification.application;

import com.cloudcomment.comment.domain.CommentCreatedEvent;
import com.cloudcomment.notification.domain.NewCommentNotification;
import com.cloudcomment.notification.domain.NotificationTarget;
import com.cloudcomment.notification.persistence.NotificationTargetRepository;
import com.cloudcomment.realtime.application.RealtimeMessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class CommentNotificationListener {

    static final String COMMENT_CREATED_TYPE = "comment.created";
    private static final int PREVIEW_LIMIT = 180;

    private final NotificationTargetRepository notificationTargetRepository;
    private final RealtimeMessagingService realtimeMessagingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCommentCreated(CommentCreatedEvent event) {
        notificationTargetRepository.findCommentTarget(event.siteId(), event.pageId())
            .ifPresent(target -> realtimeMessagingService.sendToUser(
                target.ownerId(),
                COMMENT_CREATED_TYPE,
                notification(event, target)
            ));
    }

    private NewCommentNotification notification(CommentCreatedEvent event, NotificationTarget target) {
        return new NewCommentNotification(
            event.commentId(),
            event.siteId(),
            target.siteName(),
            event.pageId(),
            target.pageUrl(),
            event.parentId(),
            event.authorEmail(),
            preview(event.content()),
            event.status(),
            event.createdAt()
        );
    }

    private String preview(String content) {
        if (content == null || content.length() <= PREVIEW_LIMIT) {
            return content;
        }
        return content.substring(0, PREVIEW_LIMIT - 1).stripTrailing() + "...";
    }
}
