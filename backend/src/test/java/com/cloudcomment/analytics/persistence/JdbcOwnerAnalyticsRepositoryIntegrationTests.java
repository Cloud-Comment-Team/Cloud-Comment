package com.cloudcomment.analytics.persistence;

import com.cloudcomment.automoderation.persistence.AutoModerationPolicyRepository;
import com.cloudcomment.analytics.domain.AnalyticsBucket;
import com.cloudcomment.analytics.domain.CommentTimePoint;
import com.cloudcomment.analytics.domain.ReactionTypeCount;
import com.cloudcomment.analytics.domain.TopPageAnalytics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcOwnerAnalyticsRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OwnerAnalyticsRepository ownerAnalyticsRepository;

    @Autowired
    private AutoModerationPolicyRepository autoModerationPolicyRepository;

    @Test
    void summarizesOwnerAnalyticsWithIsolationReactionsRepliesAndSoftDelete() {
        UUID ownerId = insertUser("owner", "Owner");
        UUID commenterA = insertUser("commenter-a", "Commenter A");
        UUID commenterB = insertUser("commenter-b", "Commenter B");
        UUID otherOwnerId = insertUser("other-owner", "Other Owner");
        UUID ownerSiteId = insertSite(ownerId, "owner-site");
        UUID secondOwnerSiteId = insertSite(ownerId, "second-owner-site");
        UUID foreignSiteId = insertSite(otherOwnerId, "foreign-site");
        UUID pageId = insertPage(ownerSiteId, "https://example.com/post");
        UUID secondPageId = insertPage(secondOwnerSiteId, "https://second.example.com/post");
        UUID foreignPageId = insertPage(foreignSiteId, "https://foreign.example.com/post");
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-05T23:59:59Z");

        UUID approvedId = insertComment(
            pageId,
            null,
            commenterA,
            "Approved root",
            "APPROVED",
            Instant.parse("2026-07-01T10:00:00Z")
        );
        insertComment(
            pageId,
            approvedId,
            commenterB,
            "Pending reply",
            "PENDING",
            Instant.parse("2026-07-02T10:00:00Z")
        );
        UUID spamId = insertComment(
            pageId,
            null,
            commenterA,
            "Spam comment",
            "SPAM",
            Instant.parse("2026-07-03T10:00:00Z")
        );
        insertComment(
            secondPageId,
            null,
            commenterB,
            "Second site comment",
            "APPROVED",
            Instant.parse("2026-07-04T10:00:00Z")
        );
        insertComment(
            pageId,
            null,
            commenterA,
            "Old rejected comment",
            "REJECTED",
            Instant.parse("2026-05-01T10:00:00Z")
        );
        UUID deletedId = insertComment(
            pageId,
            null,
            commenterA,
            "Deleted approved comment",
            "APPROVED",
            Instant.parse("2026-07-04T11:00:00Z")
        );
        softDelete(deletedId);
        insertComment(
            foreignPageId,
            null,
            commenterA,
            "Foreign comment",
            "APPROVED",
            Instant.parse("2026-07-04T10:00:00Z")
        );
        insertReaction(approvedId, commenterB, "LIKE", Instant.parse("2026-07-02T12:00:00Z"));
        insertReaction(spamId, commenterB, "LOVE", Instant.parse("2026-07-03T12:00:00Z"));
        insertAutomodDecision(approvedId, ownerSiteId, "LIVE", Instant.parse("2026-07-02T13:00:00Z"));
        insertAutomodDecision(spamId, ownerSiteId, "SHADOW", Instant.parse("2026-07-03T13:00:00Z"));
        insertAutomodDecision(approvedId, ownerSiteId, "LIVE", to);
        insertAutomodDecision(
            jdbcTemplate.queryForObject(
                "select id from comments where page_id = ? and body = 'Foreign comment'",
                UUID.class,
                foreignPageId
            ),
            foreignSiteId,
            "LIVE",
            Instant.parse("2026-07-03T13:00:00Z")
        );
        insertModerationAction(approvedId, commenterB, "APPROVE", Instant.parse("2026-07-02T14:00:00Z"));
        insertModerationAction(spamId, commenterB, "UNDO", Instant.parse("2026-07-03T14:00:00Z"));
        insertModerationAction(approvedId, commenterB, "HIDE", to);

        var summary = ownerAnalyticsRepository.summarize(ownerId, null, from, to);
        assertThat(summary.sites()).isEqualTo(2);
        assertThat(summary.pages()).isEqualTo(2);
        assertThat(summary.comments()).isEqualTo(4);
        assertThat(summary.replies()).isEqualTo(1);
        assertThat(summary.reactions()).isEqualTo(2);
        assertThat(summary.approved()).isEqualTo(2);
        assertThat(summary.pending()).isEqualTo(1);
        assertThat(summary.spam()).isEqualTo(1);
        assertThat(summary.rejected()).isZero();

        var siteSummary = ownerAnalyticsRepository.summarize(ownerId, ownerSiteId, from, to);
        assertThat(siteSummary.sites()).isEqualTo(1);
        assertThat(siteSummary.pages()).isEqualTo(1);
        assertThat(siteSummary.comments()).isEqualTo(3);

        assertThat(ownerAnalyticsRepository.findReactionDistribution(ownerId, null, from, to))
            .extracting(ReactionTypeCount::type)
            .containsExactlyInAnyOrder("LIKE", "LOVE");
        assertThat(ownerAnalyticsRepository.findCommentsOverTime(ownerId, null, from, to, AnalyticsBucket.DAY, "UTC"))
            .extracting(CommentTimePoint::bucket)
            .contains(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-02"), LocalDate.parse("2026-07-03"));

        var workload = ownerAnalyticsRepository.findWorkload(ownerId, null, from, to);
        assertThat(workload.requiringDecision()).isEqualTo(2);
        assertThat(workload.oldestPendingAt()).isEqualTo(Instant.parse("2026-07-02T10:00:00Z"));
        assertThat(workload.automaticDecisions()).isEqualTo(1);
        assertThat(workload.manualDecisions()).isEqualTo(1);
        assertThat(workload.undoActions()).isEqualTo(1);

        var previousActivity = ownerAnalyticsRepository.findPeriodActivity(ownerId, null, from, to);
        assertThat(previousActivity.comments()).isEqualTo(4);
        assertThat(previousActivity.reactions()).isEqualTo(2);
        assertThat(previousActivity.automaticDecisions()).isEqualTo(1);
        assertThat(previousActivity.manualDecisions()).isEqualTo(1);
        assertThat(previousActivity.undoActions()).isEqualTo(1);

        TopPageAnalytics topPage = ownerAnalyticsRepository.findTopPages(ownerId, null, from, to, 5).getFirst();
        assertThat(topPage.pageId()).isEqualTo(pageId);
        assertThat(topPage.comments()).isEqualTo(3);
        assertThat(topPage.reactions()).isEqualTo(2);

        assertThat(ownerAnalyticsRepository.findActiveCommenters(ownerId, null, from, to, 5))
            .extracting(commenter -> commenter.email())
            .contains(commenterEmail("commenter-a"), commenterEmail("commenter-b"));
    }

    @Test
    void bucketsCommentsByOwnerTimeZoneAndKeepsOwnerIsolation() {
        UUID ownerId = insertUser("timezone-owner", "Timezone Owner");
        UUID otherOwnerId = insertUser("timezone-other", "Other Owner");
        UUID siteId = insertSite(ownerId, "timezone-site");
        UUID foreignSiteId = insertSite(otherOwnerId, "timezone-foreign");
        UUID pageId = insertPage(siteId, "https://timezone.example.com/post");
        UUID foreignPageId = insertPage(foreignSiteId, "https://timezone-foreign.example.com/post");
        UUID authorId = insertUser("timezone-author", "Author");
        Instant createdAt = Instant.parse("2026-07-01T22:30:00Z");
        insertComment(pageId, null, authorId, "Moscow day", "APPROVED", createdAt);
        insertComment(foreignPageId, null, authorId, "Foreign day", "APPROVED", createdAt);

        var utc = ownerAnalyticsRepository.findCommentsOverTime(
            ownerId,
            null,
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-03T00:00:00Z"),
            AnalyticsBucket.DAY,
            "UTC"
        );
        var moscow = ownerAnalyticsRepository.findCommentsOverTime(
            ownerId,
            null,
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-03T00:00:00Z"),
            AnalyticsBucket.DAY,
            "Europe/Moscow"
        );

        assertThat(utc).singleElement().extracting(CommentTimePoint::bucket, CommentTimePoint::total)
            .containsExactly(LocalDate.parse("2026-07-01"), 1L);
        assertThat(moscow).singleElement().extracting(CommentTimePoint::bucket, CommentTimePoint::total)
            .containsExactly(LocalDate.parse("2026-07-02"), 1L);
    }

    private UUID insertUser(String label, String displayName) {
        return jdbcTemplate.queryForObject(
            """
                insert into app_users (email, password_hash, display_name)
                values (?, ?, ?)
                returning id
                """,
            UUID.class,
            commenterEmail(label),
            "hashed-password",
            displayName
        );
    }

    private String commenterEmail(String label) {
        return label + "@analytics.example.com";
    }

    private UUID insertSite(UUID ownerId, String label) {
        String suffix = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject(
            """
                insert into sites (owner_id, name, domain, public_key)
                values (?, ?, ?, ?)
                returning id
                """,
            UUID.class,
            ownerId,
            "Site " + label,
            label + "-" + suffix + ".example.com",
            suffix.replace("-", "")
        );
    }

    private UUID insertPage(UUID siteId, String url) {
        return jdbcTemplate.queryForObject(
            """
                insert into pages (site_id, url, title)
                values (?, ?, ?)
                returning id
                """,
            UUID.class,
            siteId,
            url + "-" + UUID.randomUUID(),
            "Page"
        );
    }

    private UUID insertComment(
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String body,
        String status,
        Instant createdAt
    ) {
        OffsetDateTime timestamp = createdAt.atOffset(ZoneOffset.UTC);
        String authorEmail = jdbcTemplate.queryForObject(
            "select email from app_users where id = ?",
            String.class,
            authorUserId
        );
        return jdbcTemplate.queryForObject(
            """
                insert into comments (
                    page_id,
                    parent_id,
                    author_user_id,
                    author_email,
                    body,
                    status,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """,
            UUID.class,
            pageId,
            parentId,
            authorUserId,
            authorEmail,
            body,
            status,
            timestamp,
            timestamp
        );
    }

    private void insertReaction(UUID commentId, UUID userId, String reactionType, Instant createdAt) {
        OffsetDateTime timestamp = createdAt.atOffset(ZoneOffset.UTC);
        jdbcTemplate.update(
            """
                insert into comment_reactions (comment_id, user_id, reaction_type, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """,
            commentId,
            userId,
            reactionType,
            timestamp,
            timestamp
        );
    }

    private void insertAutomodDecision(
        UUID commentId,
        UUID siteId,
        String executionMode,
        Instant evaluatedAt
    ) {
        autoModerationPolicyRepository.initializeFromLegacy(siteId);
        UUID policyId = jdbcTemplate.queryForObject(
            "select active_policy_version_id from site_automod_policy_state where site_id = ?",
            UUID.class,
            siteId
        );
        jdbcTemplate.update(
            """
                insert into automod_decision_events (
                    comment_id, policy_version_id, execution_mode, decision, applied_status, evaluated_at
                ) values (?, ?, ?, 'APPROVE', 'APPROVED', ?)
                """,
            commentId,
            policyId,
            executionMode,
            evaluatedAt.atOffset(ZoneOffset.UTC)
        );
    }

    private void insertModerationAction(
        UUID commentId,
        UUID moderatorId,
        String action,
        Instant createdAt
    ) {
        jdbcTemplate.update(
            """
                insert into moderation_actions (
                    comment_id, moderator_id, action, from_status, to_status, created_at
                ) values (?, ?, ?, 'PENDING', 'APPROVED', ?)
                """,
            commentId,
            moderatorId,
            action,
            createdAt.atOffset(ZoneOffset.UTC)
        );
    }

    private void softDelete(UUID commentId) {
        jdbcTemplate.update("update comments set deleted_at = now() where id = ?", commentId);
    }
}
