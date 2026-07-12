package com.cloudcomment.automoderation.application;

import com.cloudcomment.automoderation.domain.AutoModerationDecisionType;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyConfig;

final class AutoModerationDecisionRules {

    private AutoModerationDecisionRules() {
    }

    static AutoModerationDecisionType classify(int score, AutoModerationPolicyConfig config) {
        if (score >= config.spamThreshold()) {
            return AutoModerationDecisionType.SPAM;
        }
        if (score >= config.reviewThreshold()) {
            return AutoModerationDecisionType.REVIEW;
        }
        return AutoModerationDecisionType.APPROVE;
    }
}
