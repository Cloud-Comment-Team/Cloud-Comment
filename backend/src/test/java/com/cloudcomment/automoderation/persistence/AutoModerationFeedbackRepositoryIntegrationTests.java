package com.cloudcomment.automoderation.persistence;

import com.cloudcomment.automoderation.domain.AutoModerationFeedbackType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AutoModerationFeedbackRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AutoModerationPolicyRepository policyRepository;

    @Autowired
    private AutoModerationFeedbackRepository feedbackRepository;

    @Test
    void enforcesOwnerAndDecisionInvariantAndUpsertsCurrentEvaluation() {
        Fixture fixture = fixture("REVIEW");
        Instant now = Instant.parse("2026-07-13T10:00:00Z");

        var feedback = feedbackRepository.upsertCurrent(
            fixture.ownerId(), fixture.commentId(), AutoModerationFeedbackType.FALSE_POSITIVE, now
        );

        assertThat(feedback).isPresent();
        assertThat(feedback.orElseThrow().type()).isEqualTo(AutoModerationFeedbackType.FALSE_POSITIVE);
        assertThat(feedbackRepository.upsertCurrent(
            UUID.randomUUID(), fixture.commentId(), AutoModerationFeedbackType.FALSE_POSITIVE, now
        )).isEmpty();
        assertThat(feedbackRepository.upsertCurrent(
            fixture.ownerId(), fixture.commentId(), AutoModerationFeedbackType.FALSE_NEGATIVE, now
        )).isEmpty();
    }

    @Test
    void retentionDeletesOnlyFeedbackStrictlyOlderThanBoundary() {
        Fixture older = fixture("SPAM");
        Fixture boundary = fixture("REVIEW");
        Instant threshold = Instant.parse("2026-04-14T10:00:00Z");
        feedbackRepository.upsertCurrent(
            older.ownerId(), older.commentId(), AutoModerationFeedbackType.FALSE_POSITIVE,
            threshold.minusSeconds(1)
        );
        feedbackRepository.upsertCurrent(
            boundary.ownerId(), boundary.commentId(), AutoModerationFeedbackType.FALSE_POSITIVE,
            threshold
        );

        assertThat(feedbackRepository.deleteCreatedBefore(threshold)).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from automod_policy_feedback where created_at = ?",
            Integer.class,
            threshold.atOffset(ZoneOffset.UTC)
        )).isOne();
    }

    private Fixture fixture(String decision) {
        UUID ownerId = jdbcTemplate.queryForObject(
            "insert into app_users (email, password_hash) values (?, 'hash') returning id",
            UUID.class,
            UUID.randomUUID() + "@example.com"
        );
        UUID siteId = jdbcTemplate.queryForObject(
            "insert into sites (owner_id, name, domain, public_key) values (?, 'Site', ?, ?) returning id",
            UUID.class,
            ownerId,
            UUID.randomUUID() + ".example.com",
            "key-" + UUID.randomUUID()
        );
        policyRepository.initializeFromLegacy(siteId);
        UUID policyId = policyRepository.findActive(siteId).orElseThrow().version().id();
        UUID pageId = jdbcTemplate.queryForObject(
            "insert into pages (site_id, url) values (?, ?) returning id",
            UUID.class,
            siteId,
            "https://example.com/" + UUID.randomUUID()
        );
        UUID commentId = jdbcTemplate.queryForObject(
            """
                insert into comments (
                    page_id, author_email, body, status,
                    automod_policy_version_id, automod_execution_mode, automod_score,
                    automod_decision, automod_signals, automod_reason,
                    automod_applied_status, automod_evaluated_at
                )
                values (?, 'author@example.com', 'Комментарий', 'PENDING', ?, 'LIVE', 60,
                        ?, '[]'::jsonb, 'Безопасная причина', 'PENDING', now())
                returning id
                """,
            UUID.class,
            pageId,
            policyId,
            decision
        );
        return new Fixture(ownerId, commentId);
    }

    private record Fixture(UUID ownerId, UUID commentId) {
    }
}
