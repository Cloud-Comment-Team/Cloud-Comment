package com.cloudcomment.site.api;

import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import com.cloudcomment.site.domain.WidgetCornerRadius;
import com.cloudcomment.site.domain.WidgetStyle;
import com.cloudcomment.site.domain.WidgetTheme;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteRequestMappingTests {

    @Test
    void widgetStyleRequestReturnsNullForEmptyPatch() {
        WidgetStyleRequest request = new WidgetStyleRequest(null, null, null);

        assertThat(request.toDomainOrNull()).isNull();
    }

    @Test
    void widgetStyleRequestUsesDefaultsForMissingFields() {
        WidgetStyle style = new WidgetStyleRequest(WidgetTheme.DARK, null, WidgetCornerRadius.LARGE)
            .toDomainOrDefault();

        assertThat(style.theme()).isEqualTo(WidgetTheme.DARK);
        assertThat(style.accentColor()).isEqualTo(WidgetStyle.defaultStyle().accentColor());
        assertThat(style.cornerRadius()).isEqualTo(WidgetCornerRadius.LARGE);
    }

    @Test
    void widgetStylePatchUsesDefaultsForProvidedPartialFields() {
        WidgetStyle style = new WidgetStyleRequest(null, "#123abc", null)
            .toDomainOrNull();

        assertThat(style).isNotNull();
        assertThat(style.theme()).isEqualTo(WidgetStyle.defaultStyle().theme());
        assertThat(style.accentColor()).isEqualTo("#123abc");
        assertThat(style.cornerRadius()).isEqualTo(WidgetStyle.defaultStyle().cornerRadius());
    }

    @Test
    void autoModerationRequestReturnsNullForEmptyPatch() {
        AutoModerationSettingsRequest request = new AutoModerationSettingsRequest(
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertThat(request.toDomainOrNull()).isNull();
    }

    @Test
    void autoModerationRequestUsesDefaultsForMissingFields() {
        AutoModerationSettings settings = new AutoModerationSettingsRequest(
            null,
            AutoModerationStrictness.STRICT,
            List.of("casino"),
            null,
            true,
            0
        ).toDomainOrDefault();

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.strictness()).isEqualTo(AutoModerationStrictness.STRICT);
        assertThat(settings.blockedWords()).containsExactly("casino");
        assertThat(settings.holdLinks()).isEqualTo(AutoModerationSettings.defaultSettings().holdLinks());
        assertThat(settings.blockLinks()).isTrue();
        assertThat(settings.maxLinks()).isZero();
    }

    @Test
    void autoModerationPatchUsesDefaultsForProvidedPartialFields() {
        AutoModerationSettings settings = new AutoModerationSettingsRequest(
            false,
            null,
            null,
            false,
            null,
            null
        ).toDomainOrNull();

        assertThat(settings).isNotNull();
        assertThat(settings.enabled()).isFalse();
        assertThat(settings.strictness()).isEqualTo(AutoModerationSettings.defaultSettings().strictness());
        assertThat(settings.blockedWords()).isEmpty();
        assertThat(settings.holdLinks()).isFalse();
        assertThat(settings.blockLinks()).isEqualTo(AutoModerationSettings.defaultSettings().blockLinks());
        assertThat(settings.maxLinks()).isEqualTo(AutoModerationSettings.defaultSettings().maxLinks());
    }

    @Test
    void autoModerationResponseDefensivelyCopiesBlockedWords() {
        List<String> blockedWords = new ArrayList<>(List.of("spam"));
        AutoModerationSettingsResponse response = AutoModerationSettingsResponse.from(new AutoModerationSettings(
            true,
            AutoModerationStrictness.BALANCED,
            blockedWords,
            true,
            false,
            2
        ));
        blockedWords.add("mutated");

        assertThat(response.blockedWords()).containsExactly("spam");
        assertThatThrownBy(() -> response.blockedWords().add("another"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
