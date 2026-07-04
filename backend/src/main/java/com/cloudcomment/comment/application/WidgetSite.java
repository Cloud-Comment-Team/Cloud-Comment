package com.cloudcomment.comment.application;

import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.WidgetStyle;

import java.util.UUID;

public record WidgetSite(
    UUID id,
    ModerationMode moderationMode,
    WidgetStyle widgetStyle
) {

    public WidgetSite(UUID id, ModerationMode moderationMode) {
        this(id, moderationMode, WidgetStyle.defaultStyle());
    }
}
