package com.cloudcomment.automoderation.api;

import com.cloudcomment.automoderation.domain.AutoModerationCleanAction;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationLinkAction;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyConfig;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

record UpdateAutoModerationPolicyRequest(
    @NotNull @Min(1) Integer expectedRevision,
    Boolean enabled,
    AutoModerationPreset preset,
    AutoModerationExecutionMode executionMode,
    @Min(0) @Max(AutoModerationPolicyConfig.MAX_THRESHOLD) Integer reviewThreshold,
    @Min(1) @Max(AutoModerationPolicyConfig.MAX_THRESHOLD) Integer spamThreshold,
    AutoModerationCleanAction cleanAction,
    AutoModerationLinkAction linkAction,
    @Min(0) @Max(AutoModerationPolicyConfig.MAX_LINKS) Integer maxLinks,
    @Size(max = AutoModerationPolicyConfig.MAX_BLOCKED_WORDS)
    List<@Size(max = AutoModerationPolicyConfig.MAX_BLOCKED_WORD_LENGTH) String> blockedWords
) {
}
