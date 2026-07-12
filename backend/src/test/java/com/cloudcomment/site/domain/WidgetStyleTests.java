package com.cloudcomment.site.domain;

import com.cloudcomment.comment.domain.CommentReactionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WidgetStyleTests {

    @Test
    void providesVersionTwoDefaultsWithoutArbitraryMarkup() {
        WidgetStyle style = WidgetStyle.defaultStyle();

        assertThat(style.version()).isEqualTo(2);
        assertThat(style.density()).isEqualTo(WidgetDensity.COMFORTABLE);
        assertThat(style.contentWidth()).isEqualTo(WidgetContentWidth.READABLE);
        assertThat(style.enabledReactions()).containsExactly(CommentReactionType.values());
        assertThat(style.headerTitle()).isEqualTo("Комментарии");
    }

    @Test
    void rejectsMarkupAndControlCharactersInPublicLabels() {
        WidgetStyle defaults = WidgetStyle.defaultStyle();

        assertThatThrownBy(() -> new WidgetStyle(
            2, defaults.theme(), defaults.accentColor(), defaults.cornerRadius(), defaults.density(),
            defaults.contentWidth(), defaults.alignment(), defaults.fontScale(), defaults.fontFamily(), true,
            "<img src=x>", defaults.composerPosition(), defaults.defaultSort(), true, List.of(CommentReactionType.LIKE),
            defaults.avatarStyle(), defaults.elevation(), defaults.locale(), defaults.commentsTitle(),
            defaults.composerPlaceholder(), defaults.emptyMessage()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
