package com.cloudcomment.site.api;

import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.PublicCommentSort;
import com.cloudcomment.site.domain.*;

import java.util.List;

public record WidgetStyleResponse(
    int version, WidgetTheme theme, String accentColor, WidgetCornerRadius cornerRadius,
    WidgetDensity density, WidgetContentWidth contentWidth, WidgetAlignment alignment,
    WidgetFontScale fontScale, WidgetFontFamily fontFamily, boolean showHeader, String headerTitle,
    WidgetComposerPosition composerPosition, PublicCommentSort defaultSort, boolean showSort,
    List<CommentReactionType> enabledReactions, WidgetAvatarStyle avatarStyle, WidgetElevation elevation,
    WidgetLocale locale, String commentsTitle, String composerPlaceholder, String emptyMessage
) {
    public WidgetStyleResponse { enabledReactions = List.copyOf(enabledReactions); }

    public static WidgetStyleResponse from(WidgetStyle style) {
        return new WidgetStyleResponse(style.version(), style.theme(), style.accentColor(), style.cornerRadius(),
            style.density(), style.contentWidth(), style.alignment(), style.fontScale(), style.fontFamily(),
            style.showHeader(), style.headerTitle(), style.composerPosition(), style.defaultSort(), style.showSort(),
            style.enabledReactions(), style.avatarStyle(), style.elevation(), style.locale(), style.commentsTitle(),
            style.composerPlaceholder(), style.emptyMessage());
    }
}
