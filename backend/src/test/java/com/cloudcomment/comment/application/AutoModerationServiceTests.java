package com.cloudcomment.comment.application;

import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AutoModerationServiceTests {

    private final AutoModerationService service = new AutoModerationService();

    @Test
    void customBlockedWordsMarkCommentAsSpam() {
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
        assertThat(decision.reason()).contains("custom blocked words");
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
        assertThat(decision.reason()).contains("contains link");
    }

    @Test
    void disabledAutomoderationKeepsFallbackStatus() {
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
        assertThat(decision.reason()).isNull();
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
        assertThat(decision.reason()).contains("links are blocked");
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
        assertThat(decision.reason()).contains("too many links: 3");
    }

    @Test
    void defaultSpamSignalsCanEscalateToPendingOrSpam() {
        AutoModerationDecision decision = service.review(
            "Free money and crypto giveaway for everyone",
            AutoModerationSettings.defaultSettings(),
            CommentStatus.APPROVED
        );

        assertThat(decision.status()).isIn(CommentStatus.PENDING, CommentStatus.SPAM);
        assertThat(decision.reason()).contains("spam signals");
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
        assertThat(decision.reason()).contains("toxicity signals", "repeated characters");
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
        assertThat(decision.reason()).contains("too much uppercase text");
    }

    @Test
    void approvedResultDoesNotExposeLowScoreReasons() {
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
    }

    @Test
    void longModerationReasonIsBounded() {
        List<String> blockedWords = IntStream.rangeClosed(1, 60)
            .mapToObj(index -> "blockedword" + index)
            .toList();
        String content = blockedWords.stream().collect(Collectors.joining(" "));

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
