package com.cloudcomment.site.api;

import com.cloudcomment.site.domain.WidgetCornerRadius;
import com.cloudcomment.site.domain.WidgetStyle;
import com.cloudcomment.site.domain.WidgetTheme;

public record WidgetStyleResponse(
    WidgetTheme theme,
    String accentColor,
    WidgetCornerRadius cornerRadius
) {

    public static WidgetStyleResponse from(WidgetStyle style) {
        return new WidgetStyleResponse(style.theme(), style.accentColor(), style.cornerRadius());
    }
}
