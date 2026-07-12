package com.cloudcomment.automoderation.api;

import com.cloudcomment.automoderation.domain.AutoModerationCleanAction;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationLinkAction;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyLifecycle;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyVersion;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AutoModerationPolicyResponse(
    UUID id,
    UUID siteId,
    Integer version,
    int revision,
    AutoModerationPolicyLifecycle state,
    boolean enabled,
    AutoModerationPreset preset,
    AutoModerationExecutionMode executionMode,
    int reviewThreshold,
    int spamThreshold,
    AutoModerationCleanAction cleanAction,
    AutoModerationLinkAction linkAction,
    int maxLinks,
    List<String> blockedWords,
    boolean active,
    UUID basedOnVersionId,
    Instant createdAt,
    Instant updatedAt,
    Instant publishedAt
) {

    static AutoModerationPolicyResponse from(AutoModerationPolicyVersion policy, UUID activePolicyId) {
        return new AutoModerationPolicyResponse(
            policy.id(),
            policy.siteId(),
            policy.version(),
            policy.revision(),
            policy.lifecycle(),
            policy.enabled(),
            policy.preset(),
            policy.executionMode(),
            policy.config().reviewThreshold(),
            policy.config().spamThreshold(),
            policy.config().cleanAction(),
            policy.config().linkAction(),
            policy.config().maxLinks(),
            policy.config().blockedWords(),
            policy.id().equals(activePolicyId),
            policy.basedOnVersionId(),
            policy.createdAt(),
            policy.updatedAt(),
            policy.publishedAt()
        );
    }
}
