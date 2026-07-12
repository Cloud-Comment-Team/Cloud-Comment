package com.cloudcomment.site.api;

import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.PublicCommentSort;
import com.cloudcomment.site.domain.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

record WidgetStyleRequest(
    Integer version,
    WidgetTheme theme,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a #RRGGBB hex color") String accentColor,
    WidgetCornerRadius cornerRadius,
    WidgetDensity density,
    WidgetContentWidth contentWidth,
    WidgetAlignment alignment,
    WidgetFontScale fontScale,
    WidgetFontFamily fontFamily,
    Boolean showHeader,
    @Size(max = 160) @Pattern(regexp = "^[^<>\\p{Cc}]*$", message = "must not contain markup or control characters") String headerTitle,
    WidgetComposerPosition composerPosition,
    PublicCommentSort defaultSort,
    Boolean showSort,
    @Size(max = 4) List<CommentReactionType> enabledReactions,
    WidgetAvatarStyle avatarStyle,
    WidgetElevation elevation,
    WidgetLocale locale,
    @Size(max = 160) @Pattern(regexp = "^[^<>\\p{Cc}]*$", message = "must not contain markup or control characters") String commentsTitle,
    @Size(max = 160) @Pattern(regexp = "^[^<>\\p{Cc}]*$", message = "must not contain markup or control characters") String composerPlaceholder,
    @Size(max = 160) @Pattern(regexp = "^[^<>\\p{Cc}]*$", message = "must not contain markup or control characters") String emptyMessage
) {
    WidgetStyleRequest(WidgetTheme theme, String accentColor, WidgetCornerRadius cornerRadius) {
        this(null, theme, accentColor, cornerRadius, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
    }

    WidgetStyle toDomainOrDefault() { return toDomain(WidgetStyle.defaultStyle()); }

    WidgetStyle toDomainOrNull() {
        return toDomainOrNull(WidgetStyle.defaultStyle());
    }

    WidgetStyle toDomainOrNull(WidgetStyle fallback) {
        if (version == null && theme == null && accentColor == null && cornerRadius == null && density == null
            && contentWidth == null && alignment == null && fontScale == null && fontFamily == null
            && showHeader == null && headerTitle == null && composerPosition == null && defaultSort == null
            && showSort == null && enabledReactions == null && avatarStyle == null && elevation == null
            && locale == null && commentsTitle == null && composerPlaceholder == null && emptyMessage == null) {
            return null;
        }
        return toDomain(fallback);
    }

    private WidgetStyle toDomain(WidgetStyle fallback) {
        return new WidgetStyle(
            version != null ? version : fallback.version(), theme != null ? theme : fallback.theme(),
            accentColor != null ? accentColor : fallback.accentColor(),
            cornerRadius != null ? cornerRadius : fallback.cornerRadius(), density != null ? density : fallback.density(),
            contentWidth != null ? contentWidth : fallback.contentWidth(), alignment != null ? alignment : fallback.alignment(),
            fontScale != null ? fontScale : fallback.fontScale(), fontFamily != null ? fontFamily : fallback.fontFamily(),
            showHeader != null ? showHeader : fallback.showHeader(), headerTitle != null ? headerTitle : fallback.headerTitle(),
            composerPosition != null ? composerPosition : fallback.composerPosition(),
            defaultSort != null ? defaultSort : fallback.defaultSort(), showSort != null ? showSort : fallback.showSort(),
            enabledReactions != null ? enabledReactions : fallback.enabledReactions(),
            avatarStyle != null ? avatarStyle : fallback.avatarStyle(), elevation != null ? elevation : fallback.elevation(),
            locale != null ? locale : fallback.locale(), commentsTitle != null ? commentsTitle : fallback.commentsTitle(),
            composerPlaceholder != null ? composerPlaceholder : fallback.composerPlaceholder(),
            emptyMessage != null ? emptyMessage : fallback.emptyMessage()
        );
    }
}
