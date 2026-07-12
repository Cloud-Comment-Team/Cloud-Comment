package com.cloudcomment.automoderation.api;

import com.cloudcomment.automoderation.domain.AutoModerationFeedbackType;
import jakarta.validation.constraints.NotNull;

record AutoModerationFeedbackRequest(
    @NotNull AutoModerationFeedbackType type
) {
}
