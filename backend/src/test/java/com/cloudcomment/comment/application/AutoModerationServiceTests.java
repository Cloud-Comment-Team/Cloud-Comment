package com.cloudcomment.comment.application;

import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
