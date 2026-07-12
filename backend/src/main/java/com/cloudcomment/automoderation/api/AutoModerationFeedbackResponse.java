package com.cloudcomment.automoderation.api;

import com.cloudcomment.automoderation.domain.AutoModerationFeedback;
import com.cloudcomment.automoderation.domain.AutoModerationFeedbackType;

import java.time.Instant;

public record AutoModerationFeedbackResponse(
    AutoModerationFeedbackType type,
    Instant createdAt
) {

    static AutoModerationFeedbackResponse from(AutoModerationFeedback feedback) {
        return new AutoModerationFeedbackResponse(feedback.type(), feedback.createdAt());
    }
}
