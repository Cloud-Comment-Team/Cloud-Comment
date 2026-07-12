package com.cloudcomment.automoderation.domain;

import java.time.Instant;
import java.util.UUID;

public record AutoModerationFeedback(
    UUID id,
    UUID commentId,
    UUID policyVersionId,
    UUID ownerId,
    AutoModerationFeedbackType type,
    Instant createdAt
) {
}
