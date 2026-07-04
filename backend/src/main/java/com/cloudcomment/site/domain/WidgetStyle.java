package com.cloudcomment.site.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record WidgetStyle(
    WidgetTheme theme,
    String accentColor,
    WidgetCornerRadius cornerRadius
) {

    public static final String DEFAULT_ACCENT_COLOR = "#0f766e";
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public WidgetStyle {
        theme = theme != null ? theme : WidgetTheme.AUTO;
        accentColor = normalizeAccentColor(accentColor);
        cornerRadius = cornerRadius != null ? cornerRadius : WidgetCornerRadius.MEDIUM;
    }

    public static WidgetStyle defaultStyle() {
        return new WidgetStyle(WidgetTheme.AUTO, DEFAULT_ACCENT_COLOR, WidgetCornerRadius.MEDIUM);
    }

    public static boolean isValidAccentColor(String value) {
        return value != null && HEX_COLOR.matcher(value).matches();
    }

    private static String normalizeAccentColor(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_ACCENT_COLOR;
        }
        if (!isValidAccentColor(value)) {
            throw new IllegalArgumentException("accent color must be a #RRGGBB hex color");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
