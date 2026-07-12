package com.cloudcomment.automoderation.domain;

import java.time.Instant;
import java.util.UUID;

public record AutoModerationPolicyVersion(
    UUID id,
    UUID siteId,
    Integer version,
    int revision,
    AutoModerationPolicyLifecycle lifecycle,
    boolean enabled,
    AutoModerationPreset preset,
    AutoModerationExecutionMode executionMode,
    AutoModerationPolicyConfig config,
    UUID basedOnVersionId,
    Instant createdAt,
    Instant updatedAt,
    Instant publishedAt
) {
}
