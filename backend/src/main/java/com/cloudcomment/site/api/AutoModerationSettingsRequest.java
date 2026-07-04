package com.cloudcomment.site.api;

import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AutoModerationSettingsRequest(
    Boolean enabled,

    AutoModerationStrictness strictness,

    @Size(max = AutoModerationSettings.MAX_BLOCKED_WORDS)
    List<@Size(max = AutoModerationSettings.MAX_BLOCKED_WORD_LENGTH) String> blockedWords,

    Boolean holdLinks,

    Boolean blockLinks,

    @Min(0)
    @Max(AutoModerationSettings.MAX_LINKS_LIMIT)
    Integer maxLinks
) {

    AutoModerationSettings toDomainOrDefault() {
        AutoModerationSettings defaults = AutoModerationSettings.defaultSettings();
        return new AutoModerationSettings(
            enabled != null ? enabled : defaults.enabled(),
            strictness != null ? strictness : defaults.strictness(),
            blockedWords != null ? blockedWords : defaults.blockedWords(),
            holdLinks != null ? holdLinks : defaults.holdLinks(),
            blockLinks != null ? blockLinks : defaults.blockLinks(),
            maxLinks != null ? maxLinks : defaults.maxLinks()
        );
    }

    AutoModerationSettings toDomainOrNull() {
        if (enabled == null
            && strictness == null
            && blockedWords == null
            && holdLinks == null
            && blockLinks == null
            && maxLinks == null) {
            return null;
        }
        return toDomainOrDefault();
    }
}
