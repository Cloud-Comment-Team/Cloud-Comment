package com.cloudcomment.site.api;

import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.PublicCommentSort;
import com.cloudcomment.site.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WidgetStyleRequestTests {

    @Test
    void keepsLegacyThreeFieldRequestCompatible() {
        WidgetStyle style = new WidgetStyleRequest(WidgetTheme.DARK, "#123ABC", WidgetCornerRadius.LARGE)
            .toDomainOrDefault();

        assertThat(style.theme()).isEqualTo(WidgetTheme.DARK);
        assertThat(style.accentColor()).isEqualTo("#123abc");
        assertThat(style.version()).isEqualTo(2);
        assertThat(style.defaultSort()).isEqualTo(PublicCommentSort.PINNED_FIRST);
    }

    @Test
    void mapsEveryVersionTwoField() {
        WidgetStyle style = new WidgetStyleRequest(
            2, WidgetTheme.LIGHT, "#0f766e", WidgetCornerRadius.SMALL, WidgetDensity.COMPACT,
            WidgetContentWidth.WIDE, WidgetAlignment.LEFT, WidgetFontScale.LARGE, WidgetFontFamily.SERIF,
            false, "Отзывы", WidgetComposerPosition.TOP, PublicCommentSort.NEWEST, false,
            List.of(CommentReactionType.LOVE), WidgetAvatarStyle.HIDDEN, WidgetElevation.NONE, WidgetLocale.RU,
            "Обсуждение", "Оставьте отзыв", "Комментариев пока нет"
        ).toDomainOrDefault();

        assertThat(style.density()).isEqualTo(WidgetDensity.COMPACT);
        assertThat(style.contentWidth()).isEqualTo(WidgetContentWidth.WIDE);
        assertThat(style.enabledReactions()).containsExactly(CommentReactionType.LOVE);
        assertThat(style.showHeader()).isFalse();
        assertThat(style.composerPlaceholder()).isEqualTo("Оставьте отзыв");
    }

    @Test
    void legacyPatchKeepsExistingVersionTwoFields() {
        WidgetStyle existing = new WidgetStyle(
            2, WidgetTheme.DARK, "#0f766e", WidgetCornerRadius.LARGE, WidgetDensity.COMPACT,
            WidgetContentWidth.WIDE, WidgetAlignment.LEFT, WidgetFontScale.LARGE, WidgetFontFamily.MONO,
            false, "Отзывы", WidgetComposerPosition.TOP, PublicCommentSort.TOP_REACTIONS, false,
            List.of(CommentReactionType.WOW), WidgetAvatarStyle.HIDDEN, WidgetElevation.SHADOW, WidgetLocale.RU,
            "Отзывы", "Напишите отзыв", "Отзывов пока нет"
        );

        WidgetStyle merged = new WidgetStyleRequest(WidgetTheme.LIGHT, "#123abc", WidgetCornerRadius.SMALL)
            .toDomainOrNull(existing);

        assertThat(merged.theme()).isEqualTo(WidgetTheme.LIGHT);
        assertThat(merged.density()).isEqualTo(WidgetDensity.COMPACT);
        assertThat(merged.headerTitle()).isEqualTo("Отзывы");
        assertThat(merged.enabledReactions()).containsExactly(CommentReactionType.WOW);
    }
}
