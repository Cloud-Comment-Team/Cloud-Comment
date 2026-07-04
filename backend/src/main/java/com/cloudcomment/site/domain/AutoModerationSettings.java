package com.cloudcomment.site.domain;

import java.util.List;

public record AutoModerationSettings(
    boolean enabled,
    AutoModerationStrictness strictness,
    List<String> blockedWords,
    boolean holdLinks,
    boolean blockLinks,
    int maxLinks
) {

    public static final int MAX_BLOCKED_WORDS = 120;
    public static final int MAX_BLOCKED_WORD_LENGTH = 80;
    public static final int MAX_LINKS_LIMIT = 20;

    public AutoModerationSettings {
        strictness = strictness != null ? strictness : AutoModerationStrictness.BALANCED;
        blockedWords = blockedWords != null ? List.copyOf(blockedWords) : List.of();
    }

    public static AutoModerationSettings defaultSettings() {
        return new AutoModerationSettings(
            true,
            AutoModerationStrictness.BALANCED,
            List.of(),
            true,
            false,
            2
        );
    }

    public boolean active() {
        return enabled && strictness != AutoModerationStrictness.OFF;
    }
}
