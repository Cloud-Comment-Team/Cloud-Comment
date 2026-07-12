package com.cloudcomment.automoderation.domain;

import java.util.List;

public record AutoModerationPolicyConfig(
    int reviewThreshold,
    int spamThreshold,
    AutoModerationCleanAction cleanAction,
    AutoModerationLinkAction linkAction,
    int maxLinks,
    List<String> blockedWords
) {

    public static final int MAX_THRESHOLD = 1_000;
    public static final int MAX_LINKS = 20;
    public static final int MAX_BLOCKED_WORDS = 120;
    public static final int MAX_BLOCKED_WORD_LENGTH = 80;

    public AutoModerationPolicyConfig {
        blockedWords = blockedWords != null ? List.copyOf(blockedWords) : List.of();
    }

    public static AutoModerationPolicyConfig preset(AutoModerationPreset preset) {
        return switch (preset) {
            case OPEN -> new AutoModerationPolicyConfig(
                70, 130, AutoModerationCleanAction.APPROVE, AutoModerationLinkAction.REVIEW, 2, List.of()
            );
            case BALANCED -> new AutoModerationPolicyConfig(
                45, 90, AutoModerationCleanAction.APPROVE, AutoModerationLinkAction.REVIEW, 2, List.of()
            );
            case STRICT -> new AutoModerationPolicyConfig(
                25, 85, AutoModerationCleanAction.FOLLOW_SITE_MODE, AutoModerationLinkAction.REVIEW, 0, List.of()
            );
            case CUSTOM -> preset(AutoModerationPreset.BALANCED);
        };
    }
}
