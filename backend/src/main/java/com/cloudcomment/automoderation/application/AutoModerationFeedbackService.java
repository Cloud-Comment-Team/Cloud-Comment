package com.cloudcomment.automoderation.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.automoderation.domain.AutoModerationFeedback;
import com.cloudcomment.automoderation.domain.AutoModerationFeedbackType;
import com.cloudcomment.automoderation.persistence.AutoModerationFeedbackRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoModerationFeedbackService {

    static final Duration RETENTION = Duration.ofDays(90);

    private final AutoModerationFeedbackRepository repository;
    private final ResourceOwnershipService ownershipService;
    private final Clock clock;

    @Transactional
    public AutoModerationFeedback put(
        AuthenticatedUser currentUser,
        UUID commentId,
        AutoModerationFeedbackType type
    ) {
        ownershipService.assertCommentOwnedBy(currentUser, commentId);
        if (type == null) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "Feedback type is required");
        }
        return repository.upsertCurrent(currentUser.id(), commentId, type, clock.instant())
            .orElseThrow(() -> new ApplicationException(
                ApiErrorCode.BAD_REQUEST,
                "Comment has no current auto-moderation evaluation"
            ));
    }

    @Transactional
    public void delete(AuthenticatedUser currentUser, UUID commentId) {
        ownershipService.assertCommentOwnedBy(currentUser, commentId);
        if (!repository.deleteCurrent(currentUser.id(), commentId)) {
            throw new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
        }
    }

    @Scheduled(cron = "${cloud-comment.automoderation.feedback-retention-cleanup-cron:0 15 4 * * *}")
    public void cleanupExpired() {
        repository.deleteCreatedBefore(clock.instant().minus(RETENTION));
    }
}
