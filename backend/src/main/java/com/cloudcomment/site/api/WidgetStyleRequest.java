package com.cloudcomment.site.api;

import com.cloudcomment.site.domain.WidgetCornerRadius;
import com.cloudcomment.site.domain.WidgetStyle;
import com.cloudcomment.site.domain.WidgetTheme;
import jakarta.validation.constraints.Pattern;

record WidgetStyleRequest(
    WidgetTheme theme,

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a #RRGGBB hex color")
    String accentColor,

    WidgetCornerRadius cornerRadius
) {

    WidgetStyle toDomainOrDefault() {
        return toDomain(WidgetStyle.defaultStyle());
    }

    WidgetStyle toDomainOrNull() {
        if (theme == null && accentColor == null && cornerRadius == null) {
            return null;
        }
        return toDomain(WidgetStyle.defaultStyle());
    }

    private WidgetStyle toDomain(WidgetStyle fallback) {
        return new WidgetStyle(
            theme != null ? theme : fallback.theme(),
            accentColor != null ? accentColor : fallback.accentColor(),
            cornerRadius != null ? cornerRadius : fallback.cornerRadius()
        );
    }
}
