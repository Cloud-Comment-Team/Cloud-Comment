package com.cloudcomment.comment.application;

import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AutoModerationServiceTests {

    private final AutoModerationService service = new AutoModerationService();

    @Test
    void customBlockedWordsMarkCommentAsSpamAndExplainSignal() {
        AutoModerationDecision decision = service.review(
            "Normal intro, but contains Casino keyword",
            new AutoModerationSettings(
                true,
                AutoModerationStrictness.BALANCED,
                List.of("casino"),
                true,
                false,
                2
            ),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.SPAM);
        assertThat(decision.score()).isGreaterThanOrEqualTo(120);
        assertThat(decision.reason()).contains("Стоп-слово владельца: casino");
        assertThat(decision.signals()).extracting(AutoModerationSignal::category)
            .contains("CUSTOM_BLOCKED_WORD");
    }

    @Test
    void russianSpamAndObfuscationEscalateToSpam() {
        AutoModerationDecision decision = service.review(
            "К-а-з-и-н-о ставки, быстрый заработок!!!",
            AutoModerationSettings.defaultSettings(),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.SPAM);
        assertThat(decision.reason()).contains("Спам-маркер", "обфускация");
        assertThat(decision.signals()).extracting(AutoModerationSignal::category)
            .contains("SPAM_PHRASE", "OBFUSCATION");
    }

    @Test
    void leetspeakSpamIsNormalized() {
        AutoModerationDecision decision = service.review(
            "c@sin0 crypto giveaway",
            AutoModerationSettings.defaultSettings(),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.SPAM);
        assertThat(decision.signals()).extracting(AutoModerationSignal::category)
            .contains("SPAM_PHRASE");
    }

    @Test
    void singleWordRulesDoNotMatchInsideNormalWords() {
        AutoModerationDecision decision = service.review(
            "Спасибо за быстрые доставки и понятный сервис.",
            AutoModerationSettings.defaultSettings(),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.APPROVED);
        assertThat(decision.signals()).isEmpty();
    }

    @Test
    void strictModeHoldsSingleLinkForModeration() {
        AutoModerationDecision decision = service.review(
            "Look at https://example.com",
            new AutoModerationSettings(
                true,
                AutoModerationStrictness.STRICT,
                List.of(),
                true,
                false,
                2
            ),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.PENDING);
        assertThat(decision.reason()).contains("Комментарий содержит ссылку");
    }

    @Test
    void disabledAutomoderationKeepsFallbackStatusWithoutSignals() {
        AutoModerationDecision decision = service.review(
            "casino https://example.com",
            new AutoModerationSettings(
                false,
                AutoModerationStrictness.OFF,
                List.of("casino"),
                true,
                true,
                0
            ),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.APPROVED);
        assertThat(decision.score()).isZero();
        assertThat(decision.reason()).isNull();
        assertThat(decision.signals()).isEmpty();
    }

    @Test
    void linkBlockingMarksAnyLinkAsSpam() {
        AutoModerationDecision decision = service.review(
            "Look at https://spam.example.com",
            new AutoModerationSettings(
                true,
                AutoModerationStrictness.RELAXED,
                List.of(),
                true,
                true,
                20
            ),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.SPAM);
        assertThat(decision.reason()).contains("Ссылки запрещены");
        assertThat(decision.signals()).extracting(AutoModerationSignal::category)
            .contains("BLOCKED_LINK");
    }

    @Test
    void balancedModeHoldsLinkFloodForModeration() {
        AutoModerationDecision decision = service.review(
            "Links: https://a.example.com https://b.example.com https://c.example.com",
            new AutoModerationSettings(
                true,
                AutoModerationStrictness.BALANCED,
                List.of(),
                true,
                false,
                1
            ),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.PENDING);
        assertThat(decision.reason()).contains("Слишком много ссылок: 3");
    }

    @Test
    void relaxedBalancedAndStrictModesUseDifferentThresholds() {
        AutoModerationSettings relaxed = new AutoModerationSettings(
            true,
            AutoModerationStrictness.RELAXED,
            List.of(),
            true,
            false,
            2
        );
        AutoModerationSettings strict = new AutoModerationSettings(
            true,
            AutoModerationStrictness.STRICT,
            List.of(),
            true,
            false,
            2
        );

        assertThat(service.review("https://example.com", relaxed, CommentStatus.APPROVED).status())
            .isEqualTo(CommentStatus.APPROVED);
        assertThat(service.review("https://example.com", strict, CommentStatus.APPROVED).status())
            .isEqualTo(CommentStatus.PENDING);
    }

    @Test
    void defaultSpamSignalsCanEscalateToSpam() {
        AutoModerationDecision decision = service.review(
            "Free money and crypto giveaway for everyone",
            AutoModerationSettings.defaultSettings(),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.SPAM);
        assertThat(decision.reason()).contains("Спам-маркер");
    }

    @Test
    void balancedModeHoldsRepeatedCharactersAndToxicSignals() {
        AutoModerationDecision decision = service.review(
            "scam aaaaaa",
            new AutoModerationSettings(
                true,
                AutoModerationStrictness.BALANCED,
                List.of(),
                false,
                false,
                2
            ),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.PENDING);
        assertThat(decision.reason()).contains("Токсичный маркер", "повторяющихся символов");
    }

    @Test
    void suspiciousContactsHoldForModeration() {
        AutoModerationDecision decision = service.review(
            "Пишите в telegram @fast_money_bot",
            AutoModerationSettings.defaultSettings(),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.PENDING);
        assertThat(decision.reason()).contains("мессенджер");
    }

    @Test
    void strictModeHoldsAggressiveUppercaseText() {
        AutoModerationDecision decision = service.review(
            "THIS COMMENT IS VERY LOUD AND AGGRESSIVE",
            new AutoModerationSettings(
                true,
                AutoModerationStrictness.STRICT,
                List.of(),
                false,
                false,
                2
            ),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.PENDING);
        assertThat(decision.reason()).contains("капсом");
    }

    @Test
    void approvedResultDoesNotPersistLowScoreReasonsButKeepsPreviewSignals() {
        AutoModerationDecision decision = service.review(
            "Look at https://example.com",
            new AutoModerationSettings(
                true,
                AutoModerationStrictness.RELAXED,
                List.of(),
                true,
                false,
                2
            ),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.APPROVED);
        assertThat(decision.reason()).isNull();
        assertThat(decision.signals()).extracting(AutoModerationSignal::category)
            .contains("CONTAINS_LINK");
    }

    @Test
    void cleanCommentDoesNotReceiveReasonOrSignals() {
        AutoModerationDecision decision = service.review(
            "Спасибо за полезный материал, помогло разобраться.",
            AutoModerationSettings.defaultSettings(),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.APPROVED);
        assertThat(decision.score()).isZero();
        assertThat(decision.reason()).isNull();
        assertThat(decision.signals()).isEmpty();
    }

    @Test
    void longModerationReasonIsBounded() {
        List<String> blockedWords = IntStream.rangeClosed(1, 8)
            .mapToObj(index -> "blockedword-" + index + "-" + "x".repeat(70))
            .toList();
        String content = String.join(" ", blockedWords);

        AutoModerationDecision decision = service.review(
            content,
            new AutoModerationSettings(
                true,
                AutoModerationStrictness.BALANCED,
                blockedWords,
                false,
                false,
                2
            ),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isEqualTo(CommentStatus.SPAM);
        assertThat(decision.reason()).hasSizeLessThanOrEqualTo(500).endsWith("...");
    }
}
