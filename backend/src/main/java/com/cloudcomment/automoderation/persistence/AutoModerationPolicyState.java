package com.cloudcomment.automoderation.persistence;

import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;

import java.util.UUID;

public record AutoModerationPolicyState(
    UUID siteId,
    UUID activePolicyVersionId,
    boolean enabled,
    AutoModerationExecutionMode executionMode,
    int lastPublishedVersion,
    String legacySettingsFingerprint
) {
}
