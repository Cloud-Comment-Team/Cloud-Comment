package com.cloudcomment.moderation.api;

import com.cloudcomment.moderation.domain.ModerationActionType;
import jakarta.validation.constraints.NotNull;

public record ApplyModerationActionRequest(
    @NotNull ModerationActionType action,
    String reason
) {
}
