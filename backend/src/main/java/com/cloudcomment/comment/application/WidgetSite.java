package com.cloudcomment.comment.application;

import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.WidgetStyle;

import java.util.UUID;

public record WidgetSite(
    UUID id,
    ModerationMode moderationMode,
    WidgetStyle widgetStyle,
    AutoModerationSettings autoModeration
) {

    public WidgetSite {
        widgetStyle = widgetStyle != null ? widgetStyle : WidgetStyle.defaultStyle();
        autoModeration = autoModeration != null ? autoModeration : AutoModerationSettings.defaultSettings();
    }

    public WidgetSite(UUID id, ModerationMode moderationMode) {
        this(id, moderationMode, WidgetStyle.defaultStyle(), AutoModerationSettings.defaultSettings());
    }

    public WidgetSite(UUID id, ModerationMode moderationMode, WidgetStyle widgetStyle) {
        this(id, moderationMode, widgetStyle, AutoModerationSettings.defaultSettings());
    }
}
