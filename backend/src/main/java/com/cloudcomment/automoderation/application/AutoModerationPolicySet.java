package com.cloudcomment.automoderation.application;

import com.cloudcomment.automoderation.domain.AutoModerationPolicyVersion;

import java.util.List;

public record AutoModerationPolicySet(
    AutoModerationPolicyVersion activePolicy,
    AutoModerationPolicyVersion draft,
    List<AutoModerationPolicyVersion> versions
) {

    public AutoModerationPolicySet {
        versions = List.copyOf(versions);
    }
}
