package com.cloudcomment.automoderation.api;

import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;

record CreateAutoModerationPolicyRequest(
    AutoModerationPreset preset,
    Boolean enabled,
    AutoModerationExecutionMode executionMode
) {
}
