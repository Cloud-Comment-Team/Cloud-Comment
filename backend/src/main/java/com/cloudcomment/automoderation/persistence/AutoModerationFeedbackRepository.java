package com.cloudcomment.automoderation.persistence;

import com.cloudcomment.automoderation.domain.AutoModerationFeedback;
import com.cloudcomment.automoderation.domain.AutoModerationFeedbackType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AutoModerationFeedbackRepository {

    Optional<AutoModerationFeedback> upsertCurrent(
        UUID ownerId,
        UUID commentId,
        AutoModerationFeedbackType type,
        Instant createdAt
    );

    boolean deleteCurrent(UUID ownerId, UUID commentId);

    int deleteCreatedBefore(Instant threshold);
}
