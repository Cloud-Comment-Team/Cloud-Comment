package com.cloudcomment.site.domain;

import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.PublicCommentSort;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public record WidgetStyle(
    int version,
    WidgetTheme theme,
    String accentColor,
    WidgetCornerRadius cornerRadius,
    WidgetDensity density,
    WidgetContentWidth contentWidth,
    WidgetAlignment alignment,
    WidgetFontScale fontScale,
    WidgetFontFamily fontFamily,
    boolean showHeader,
    String headerTitle,
    WidgetComposerPosition composerPosition,
    PublicCommentSort defaultSort,
    boolean showSort,
    List<CommentReactionType> enabledReactions,
    WidgetAvatarStyle avatarStyle,
    WidgetElevation elevation,
    WidgetLocale locale,
    String commentsTitle,
    String composerPlaceholder,
    String emptyMessage
) {

    public static final int CURRENT_VERSION = 2;
    public static final String DEFAULT_ACCENT_COLOR = "#0f766e";
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public WidgetStyle {
        version = version >= CURRENT_VERSION ? version : CURRENT_VERSION;
        theme = theme != null ? theme : WidgetTheme.AUTO;
        accentColor = normalizeAccentColor(accentColor);
        cornerRadius = cornerRadius != null ? cornerRadius : WidgetCornerRadius.MEDIUM;
        density = density != null ? density : WidgetDensity.COMFORTABLE;
        contentWidth = contentWidth != null ? contentWidth : WidgetContentWidth.READABLE;
        alignment = alignment != null ? alignment : WidgetAlignment.CENTER;
        fontScale = fontScale != null ? fontScale : WidgetFontScale.MEDIUM;
        fontFamily = fontFamily != null ? fontFamily : WidgetFontFamily.INHERIT;
        headerTitle = safeLabel(headerTitle, "Комментарии");
        composerPosition = composerPosition != null ? composerPosition : WidgetComposerPosition.BOTTOM;
        defaultSort = defaultSort != null ? defaultSort : PublicCommentSort.PINNED_FIRST;
        enabledReactions = enabledReactions == null
            ? List.of(CommentReactionType.values())
            : List.copyOf(enabledReactions);
        avatarStyle = avatarStyle != null ? avatarStyle : WidgetAvatarStyle.INITIALS;
        elevation = elevation != null ? elevation : WidgetElevation.BORDER;
        locale = locale != null ? locale : WidgetLocale.RU;
        commentsTitle = safeLabel(commentsTitle, "Комментарии");
        composerPlaceholder = safeLabel(composerPlaceholder, "Напишите комментарий");
        emptyMessage = safeLabel(emptyMessage, "Пока нет комментариев. Будьте первым, кто начнет обсуждение.");
    }

    public WidgetStyle(WidgetTheme theme, String accentColor, WidgetCornerRadius cornerRadius) {
        this(CURRENT_VERSION, theme, accentColor, cornerRadius, WidgetDensity.COMFORTABLE,
            WidgetContentWidth.READABLE, WidgetAlignment.CENTER, WidgetFontScale.MEDIUM,
            WidgetFontFamily.INHERIT, true, "Комментарии", WidgetComposerPosition.BOTTOM,
            PublicCommentSort.PINNED_FIRST, true, List.of(CommentReactionType.values()),
            WidgetAvatarStyle.INITIALS, WidgetElevation.BORDER, WidgetLocale.RU,
            "Комментарии", "Напишите комментарий",
            "Пока нет комментариев. Будьте первым, кто начнет обсуждение.");
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

    private static String safeLabel(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        if (normalized.length() > 160 || normalized.chars().anyMatch(ch -> ch < 32 || ch == '<' || ch == '>')) {
            throw new IllegalArgumentException("widget label contains unsafe characters or is too long");
        }
        return normalized;
    }
}
