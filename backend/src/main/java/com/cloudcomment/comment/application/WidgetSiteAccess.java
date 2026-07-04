package com.cloudcomment.comment.application;

import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.WidgetStyle;

import java.util.UUID;

public record WidgetSiteAccess(
    UUID siteId,
    ModerationMode moderationMode,
    WidgetStyle widgetStyle,
    String origin
) {

    public WidgetSiteAccess(UUID siteId, ModerationMode moderationMode, String origin) {
        this(siteId, moderationMode, WidgetStyle.defaultStyle(), origin);
    }
}
