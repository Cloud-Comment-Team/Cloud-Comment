package com.cloudcomment.site.api;

import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;

import java.util.List;

public record AutoModerationSettingsResponse(
    boolean enabled,
    AutoModerationStrictness strictness,
    List<String> blockedWords,
    boolean holdLinks,
    boolean blockLinks,
    int maxLinks
) {

    public AutoModerationSettingsResponse {
        blockedWords = List.copyOf(blockedWords);
    }

    static AutoModerationSettingsResponse from(AutoModerationSettings settings) {
        return new AutoModerationSettingsResponse(
            settings.enabled(),
            settings.strictness(),
            settings.blockedWords(),
            settings.holdLinks(),
            settings.blockLinks(),
            settings.maxLinks()
        );
    }
}
