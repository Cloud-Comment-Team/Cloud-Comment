package com.cloudcomment.automoderation.api;

import com.cloudcomment.automoderation.application.AutoModerationPolicySet;

import java.util.List;
import java.util.UUID;

record AutoModerationPoliciesResponse(
    AutoModerationPolicyResponse activePolicy,
    AutoModerationPolicyResponse draft,
    List<AutoModerationPolicyResponse> versions
) {

    static AutoModerationPoliciesResponse from(AutoModerationPolicySet policies) {
        UUID activeId = policies.activePolicy().id();
        return new AutoModerationPoliciesResponse(
            AutoModerationPolicyResponse.from(policies.activePolicy(), activeId),
            policies.draft() != null ? AutoModerationPolicyResponse.from(policies.draft(), activeId) : null,
            policies.versions().stream()
                .map(policy -> AutoModerationPolicyResponse.from(policy, activeId))
                .toList()
        );
    }
}
