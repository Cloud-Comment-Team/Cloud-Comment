package com.cloudcomment.automoderation.domain;

public record ActiveAutoModerationPolicy(
    AutoModerationPolicyVersion version,
    boolean enabled,
    AutoModerationExecutionMode executionMode
) {
}
