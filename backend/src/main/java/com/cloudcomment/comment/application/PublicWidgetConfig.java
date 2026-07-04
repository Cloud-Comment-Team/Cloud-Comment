package com.cloudcomment.comment.application;

import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.WidgetStyle;

import java.util.UUID;

public record PublicWidgetConfig(
    UUID siteId,
    ModerationMode moderationMode,
    WidgetStyle widgetStyle
) {

    public PublicWidgetConfig(UUID siteId, ModerationMode moderationMode) {
        this(siteId, moderationMode, WidgetStyle.defaultStyle());
    }
}
