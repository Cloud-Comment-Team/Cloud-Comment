package com.cloudcomment.site.persistence;

import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.WidgetStyle;

public record SiteUpdate(
    String name,
    String domain,
    ModerationMode moderationMode,
    Boolean active,
    WidgetStyle widgetStyle,
    AutoModerationSettings autoModeration
) {

    public SiteUpdate(
        String name,
        String domain,
        ModerationMode moderationMode,
        Boolean active
    ) {
        this(name, domain, moderationMode, active, null, null);
    }

    public boolean hasChanges() {
        return name != null
            || domain != null
            || moderationMode != null
            || active != null
            || widgetStyle != null
            || autoModeration != null;
    }
}
