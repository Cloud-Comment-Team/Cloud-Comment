package com.cloudcomment.moderation.api;

import com.cloudcomment.moderation.domain.ModerationActionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkModerationRequest(
    @NotNull UUID operationId,
    @NotNull @Size(min = 1, max = 50) List<@NotNull UUID> commentIds,
    @NotNull ModerationActionType action,
    @Size(max = 1000) String reason
) {
}
