package com.cloudcomment.comment.persistence;

import com.cloudcomment.automoderation.domain.AutoModerationDecisionType;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationSnapshot;
import com.cloudcomment.automoderation.persistence.AutoModerationPolicyRepository;
import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.domain.PublicCommentSort;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.WidgetDensity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcPublicCommentRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PublicCommentRepository repository;

    @Autowired
    private AutoModerationPolicyRepository autoModerationPolicyRepository;

    @Test
    void resolvesActiveSiteAllowedOriginsAndCreatesPageCommentsTransactionally() {
        UUID ownerId = insertUser("owner", "Owner Name");
        UUID visitorId = insertUser("visitor", "Visitor Name");
        UUID siteId = insertSite(ownerId, "example.com", "https://example.com", ModerationMode.PRE_MODERATION, true);
        UUID inactiveSiteId = insertSite(ownerId, "inactive.example.com", "https://inactive.example.com", ModerationMode.POST_MODERATION, false);
        jdbcTemplate.update("update sites set widget_style_config = widget_style_config || ?::jsonb where id = ?",
            "{\"density\":\"COMPACT\",\"headerTitle\":\"Отзывы\"}", siteId);
        String pageUrl = "https://example.com/blog/post-1";

        assertThat(repository.findActiveSite(siteId))
            .hasValueSatisfying(site -> {
                assertThat(site.id()).isEqualTo(siteId);
                assertThat(site.moderationMode()).isEqualTo(ModerationMode.PRE_MODERATION);
                assertThat(site.widgetStyle().accentColor()).isEqualTo("#0f766e");
                assertThat(site.widgetStyle().density()).isEqualTo(WidgetDensity.COMPACT);
                assertThat(site.widgetStyle().headerTitle()).isEqualTo("Отзывы");
                assertThat(site.autoModeration().enabled()).isTrue();
            });
        assertThat(repository.findActiveSite(inactiveSiteId)).isEmpty();
        assertThat(repository.isAllowedOrigin(siteId, "https://example.com")).isTrue();
        assertThat(repository.isAllowedOrigin(siteId, "https://other.example.com")).isFalse();
        assertThat(repository.isAllowedOrigin(inactiveSiteId, "https://example.com")).isFalse();

        UUID pageId = repository.findOrCreatePage(siteId, pageUrl);
        assertThat(repository.findOrCreatePage(siteId, pageUrl)).isEqualTo(pageId);
        assertThat(repository.findPageId(siteId, pageUrl)).contains(pageId);

        CommentView pendingRoot = repository.createComment(
            siteId,
            pageId,
            null,
            visitorId,
            "Visitor Name",
            "visitor@example.com",
            "Pending root",
            CommentStatus.PENDING
        );
        CommentView approvedRoot = repository.createComment(
            siteId,
            pageId,
            null,
            visitorId,
            "Visitor Name",
            "visitor@example.com",
            "Approved root",
            CommentStatus.APPROVED
        );
        repository.createComment(
            siteId,
            pageId,
            null,
            visitorId,
            "Visitor Name",
            "visitor@example.com",
            "Hidden root",
            CommentStatus.HIDDEN
        );
        CommentView approvedReply = repository.createComment(
            siteId,
            pageId,
            approvedRoot.id(),
            visitorId,
            "Visitor Name",
            "visitor@example.com",
            "Approved reply",
            CommentStatus.APPROVED
        );
        repository.createComment(
            siteId,
            pageId,
            approvedReply.id(),
            visitorId,
            "Visitor Name",
            "visitor@example.com",
            "Nested approved reply",
            CommentStatus.APPROVED
        );
        repository.createComment(
            siteId,
            pageId,
            approvedRoot.id(),
            visitorId,
            "Visitor Name",
            "visitor@example.com",
            "Pending reply",
            CommentStatus.PENDING
        );
        repository.setReaction(approvedRoot.id(), visitorId, CommentReactionType.LOVE);
        repository.setReaction(approvedRoot.id(), ownerId, CommentReactionType.LIKE);
        repository.setReaction(approvedReply.id(), visitorId, CommentReactionType.WOW);

        CommentPage comments = repository.findApprovedComments(siteId, pageId, 1, 20, Optional.of(visitorId));

        assertThat(comments.totalItems()).isEqualTo(1);
        assertThat(comments.items()).singleElement().satisfies(root -> {
            assertThat(root.id()).isEqualTo(approvedRoot.id());
            assertThat(root.content()).isEqualTo("Approved root");
            assertThat(root.author().id()).isEqualTo(visitorId);
            assertThat(root.author().email()).isEqualTo("visitor@example.com");
            assertThat(root.author().displayName()).isEqualTo("Visitor Name");
            assertThat(root.status()).isEqualTo(CommentStatus.APPROVED);
            assertThat(root.ownedByCurrentUser()).isTrue();
            assertThat(root.reactions())
                .filteredOn(reaction -> reaction.type() == CommentReactionType.LOVE)
                .singleElement()
                .satisfies(reaction -> {
                    assertThat(reaction.count()).isEqualTo(1);
                    assertThat(reaction.reactedByCurrentUser()).isTrue();
                });
            assertThat(root.reactions())
                .filteredOn(reaction -> reaction.type() == CommentReactionType.LIKE)
                .singleElement()
                .satisfies(reaction -> {
                    assertThat(reaction.count()).isEqualTo(1);
                    assertThat(reaction.reactedByCurrentUser()).isFalse();
                });
            assertThat(root.replies()).singleElement().satisfies(reply -> {
                assertThat(reply.id()).isEqualTo(approvedReply.id());
                assertThat(reply.content()).isEqualTo("Approved reply");
                assertThat(reply.reactions())
                    .filteredOn(reaction -> reaction.type() == CommentReactionType.WOW)
                    .singleElement()
                    .satisfies(reaction -> {
                        assertThat(reaction.count()).isEqualTo(1);
                        assertThat(reaction.reactedByCurrentUser()).isTrue();
                    });
                assertThat(reply.replies()).isEmpty();
            });
        });

        assertThat(repository.existsApprovedCommentInSite(siteId, approvedRoot.id())).isTrue();
        assertThat(repository.existsApprovedCommentInSite(siteId, pendingRoot.id())).isFalse();
        assertThat(repository.clearReaction(approvedRoot.id(), visitorId))
            .filteredOn(reaction -> reaction.type() == CommentReactionType.LOVE)
            .singleElement()
            .satisfies(reaction -> {
                assertThat(reaction.count()).isZero();
                assertThat(reaction.reactedByCurrentUser()).isFalse();
            });
        assertThat(repository.existsApprovedRootCommentOnPage(pageId, approvedRoot.id())).isTrue();
        assertThat(repository.existsApprovedRootCommentOnPage(pageId, approvedReply.id())).isFalse();
        assertThat(repository.existsApprovedRootCommentOnPage(pageId, pendingRoot.id())).isFalse();
        assertThat(repository.existsApprovedRootCommentOnPage(pageId, UUID.randomUUID())).isFalse();

        CommentView updatedRoot = repository.updateOwnComment(
            siteId,
            approvedRoot.id(),
            visitorId,
            "Updated approved root",
            CommentStatus.PENDING,
            "Needs another look"
        ).orElseThrow();
        assertThat(updatedRoot.content()).isEqualTo("Updated approved root");
        assertThat(updatedRoot.status()).isEqualTo(CommentStatus.PENDING);
        assertThat(updatedRoot.editedAt()).isNotNull();
        assertThat(updatedRoot.ownedByCurrentUser()).isTrue();
        assertThat(repository.findApprovedComments(siteId, pageId, 1, 20, Optional.of(visitorId)).items()).isEmpty();

        assertThat(repository.softDeleteOwnComment(siteId, approvedReply.id(), ownerId)).isFalse();
        assertThat(repository.softDeleteOwnComment(siteId, approvedReply.id(), visitorId)).isTrue();
        assertThat(repository.existsApprovedCommentInSite(siteId, approvedReply.id())).isFalse();
    }

    @Test
    void limitsInlineRepliesAndPagesTheCompleteBranch() {
        UUID ownerId = insertUser("reply-owner", "Reply Owner");
        UUID visitorId = insertUser("reply-visitor", "Reply Visitor");
        UUID siteId = insertSite(ownerId, "replies.example.com", "https://replies.example.com", ModerationMode.POST_MODERATION, true);
        UUID pageId = repository.findOrCreatePage(siteId, "https://replies.example.com/article");
        CommentView root = repository.createComment(
            siteId, pageId, null, visitorId, "Reply Visitor", "reply-visitor@example.com", "Root", CommentStatus.APPROVED
        );
        repository.createComment(
            siteId, pageId, root.id(), visitorId, "Reply Visitor", "reply-visitor@example.com", "First", CommentStatus.APPROVED
        );
        repository.createComment(
            siteId, pageId, root.id(), visitorId, "Reply Visitor", "reply-visitor@example.com", "Second", CommentStatus.APPROVED
        );

        CommentPage limited = repository.findApprovedComments(
            siteId, pageId, 1, 20, PublicCommentSort.PINNED_FIRST, Optional.of(visitorId), 1
        );
        assertThat(limited.items()).singleElement().satisfies(comment -> {
            assertThat(comment.replyCount()).isEqualTo(2);
            assertThat(comment.replies()).singleElement().extracting(CommentView::content).isEqualTo("First");
        });

        CommentPage secondPage = repository.findApprovedReplies(siteId, root.id(), 2, 1, Optional.of(visitorId));
        assertThat(secondPage.totalItems()).isEqualTo(2);
        assertThat(secondPage.items()).singleElement().extracting(CommentView::content).isEqualTo("Second");
    }

    @Test
    void appendsImmutableAutomodDecisionEventsForCreateAndEdit() {
        UUID ownerId = insertUser("event-owner", "Event Owner");
        UUID visitorId = insertUser("event-visitor", "Event Visitor");
        UUID siteId = insertSite(
            ownerId,
            "events.example.com",
            "https://events.example.com",
            ModerationMode.POST_MODERATION,
            true
        );
        autoModerationPolicyRepository.initializeFromLegacy(siteId);
        UUID policyId = jdbcTemplate.queryForObject(
            "select active_policy_version_id from site_automod_policy_state where site_id = ?",
            UUID.class,
            siteId
        );
        UUID pageId = repository.findOrCreatePage(siteId, "https://events.example.com/article");
        Instant createdEvaluationAt = Instant.parse("2026-07-01T10:00:00Z");
        Instant editedEvaluationAt = Instant.parse("2026-07-02T11:00:00Z");

        CommentView comment = repository.createComment(
            siteId,
            pageId,
            null,
            visitorId,
            "Visitor",
            "visitor@example.com",
            "Created with automoderation",
            CommentStatus.APPROVED,
            null,
            snapshot(policyId, AutoModerationExecutionMode.LIVE, createdEvaluationAt)
        );
        repository.updateOwnComment(
            siteId,
            comment.id(),
            visitorId,
            "Edited with automoderation",
            CommentStatus.PENDING,
            null,
            snapshot(policyId, AutoModerationExecutionMode.SHADOW, editedEvaluationAt)
        ).orElseThrow();
        repository.updateOwnComment(
            siteId,
            comment.id(),
            visitorId,
            "Edited without automoderation",
            CommentStatus.APPROVED,
            null
        ).orElseThrow();

        assertThat(jdbcTemplate.queryForList(
            """
                select execution_mode, decision, applied_status, evaluated_at
                from automod_decision_events
                where comment_id = ?
                order by evaluated_at
                """,
            comment.id()
        )).hasSize(2)
            .satisfiesExactly(
                event -> {
                    assertThat(event.get("execution_mode")).isEqualTo("LIVE");
                    assertThat(event.get("decision")).isEqualTo("APPROVE");
                    assertThat(((java.sql.Timestamp) event.get("evaluated_at")).toInstant())
                        .isEqualTo(createdEvaluationAt);
                },
                event -> {
                    assertThat(event.get("execution_mode")).isEqualTo("SHADOW");
                    assertThat(event.get("applied_status")).isEqualTo("APPROVED");
                    assertThat(((java.sql.Timestamp) event.get("evaluated_at")).toInstant())
                        .isEqualTo(editedEvaluationAt);
                }
            );
    }

    @Test
    void databaseTriggerCapturesRollbackStyleWritesOnlyForCompleteChangedSnapshots() {
        UUID ownerId = insertUser("trigger-owner", "Trigger Owner");
        UUID visitorId = insertUser("trigger-visitor", "Trigger Visitor");
        UUID siteId = insertSite(
            ownerId,
            "trigger.example.com",
            "https://trigger.example.com",
            ModerationMode.POST_MODERATION,
            true
        );
        autoModerationPolicyRepository.initializeFromLegacy(siteId);
        UUID policyId = jdbcTemplate.queryForObject(
            "select active_policy_version_id from site_automod_policy_state where site_id = ?",
            UUID.class,
            siteId
        );
        UUID pageId = repository.findOrCreatePage(siteId, "https://trigger.example.com/article");
        Instant firstEvaluationAt = Instant.parse("2026-07-03T10:00:00Z");
        Instant secondEvaluationAt = Instant.parse("2026-07-04T11:00:00Z");

        UUID commentId = jdbcTemplate.queryForObject(
            """
                insert into comments (
                    page_id, author_user_id, body, status,
                    automod_policy_version_id, automod_execution_mode, automod_score,
                    automod_decision, automod_signals, automod_reason,
                    automod_applied_status, automod_evaluated_at
                )
                values (?, ?, 'Rollback create', 'APPROVED', ?, 'LIVE', 10,
                        'APPROVE', '[]'::jsonb, null, 'APPROVED', ?)
                returning id
                """,
            UUID.class,
            pageId,
            visitorId,
            policyId,
            firstEvaluationAt.atOffset(ZoneOffset.UTC)
        );
        assertThat(decisionEventCount(commentId)).isOne();

        jdbcTemplate.update(
            """
                update comments
                set body = 'Rollback edit',
                    status = 'SPAM',
                    automod_score = 150,
                    automod_decision = 'SPAM',
                    automod_signals = '[{"category":"LINK","score":150}]'::jsonb,
                    automod_reason = 'Automoderation: LINK',
                    automod_applied_status = 'SPAM',
                    automod_evaluated_at = ?
                where id = ?
                """,
            secondEvaluationAt.atOffset(ZoneOffset.UTC),
            commentId
        );
        assertThat(decisionEventCount(commentId)).isEqualTo(2);

        jdbcTemplate.update("update comments set body = 'Unrelated edit' where id = ?", commentId);
        assertThat(decisionEventCount(commentId)).isEqualTo(2);

        jdbcTemplate.update(
            """
                update comments
                set automod_policy_version_id = null,
                    automod_execution_mode = null,
                    automod_score = null,
                    automod_decision = null,
                    automod_signals = null,
                    automod_reason = null,
                    automod_applied_status = null,
                    automod_evaluated_at = null
                where id = ?
                """,
            commentId
        );
        assertThat(decisionEventCount(commentId)).isEqualTo(2);
    }

    @Test
    void sortsPinnedRootsFirstAndCountsReactionsAcrossApprovedThread() {
        UUID ownerId = insertUser("sorting-owner", "Sorting Owner");
        UUID visitorId = insertUser("sorting-visitor", "Sorting Visitor");
        UUID siteId = insertSite(ownerId, "sorting.example.com", "https://sorting.example.com", ModerationMode.POST_MODERATION, true);
        UUID pageId = repository.findOrCreatePage(siteId, "https://sorting.example.com/article");

        CommentView older = repository.createComment(siteId, pageId, null, visitorId, "Visitor", "visitor@example.com", "Older", CommentStatus.APPROVED);
        CommentView newer = repository.createComment(siteId, pageId, null, visitorId, "Visitor", "visitor@example.com", "Newer", CommentStatus.APPROVED);
        CommentView pinned = repository.createComment(siteId, pageId, null, visitorId, "Visitor", "visitor@example.com", "Pinned", CommentStatus.APPROVED);
        CommentView reply = repository.createComment(siteId, pageId, older.id(), ownerId, "Owner", "owner@example.com", "Popular reply", CommentStatus.APPROVED);

        jdbcTemplate.update("update comments set created_at = ? where id = ?", OffsetDateTime.parse("2026-01-01T00:00:00Z"), older.id());
        jdbcTemplate.update("update comments set created_at = ? where id = ?", OffsetDateTime.parse("2026-01-02T00:00:00Z"), newer.id());
        jdbcTemplate.update("update comments set created_at = ?, is_pinned = true where id = ?", OffsetDateTime.parse("2026-01-03T00:00:00Z"), pinned.id());
        repository.setReaction(older.id(), ownerId, CommentReactionType.LIKE);
        repository.setReaction(reply.id(), visitorId, CommentReactionType.LOVE);

        assertThat(repository.findApprovedComments(siteId, pageId, 1, 20, PublicCommentSort.PINNED_FIRST, Optional.empty()).items())
            .extracting(CommentView::id)
            .containsExactly(pinned.id(), older.id(), newer.id());
        assertThat(repository.findApprovedComments(siteId, pageId, 1, 20, PublicCommentSort.NEWEST, Optional.empty()).items())
            .extracting(CommentView::id)
            .containsExactly(pinned.id(), newer.id(), older.id());
        assertThat(repository.findApprovedComments(siteId, pageId, 1, 20, PublicCommentSort.OLDEST, Optional.empty()).items())
            .extracting(CommentView::id)
            .containsExactly(pinned.id(), older.id(), newer.id());
        assertThat(repository.findApprovedComments(siteId, pageId, 1, 20, PublicCommentSort.TOP_REACTIONS, Optional.empty()).items())
            .extracting(CommentView::id)
            .containsExactly(pinned.id(), older.id(), newer.id());
        assertThat(repository.findApprovedComments(siteId, pageId, 1, 20, PublicCommentSort.NEWEST, Optional.empty()).items().getFirst().pinned())
            .isTrue();
    }

    private UUID insertUser(String label, String displayName) {
        return jdbcTemplate.queryForObject(
            """
                insert into app_users (email, password_hash, display_name)
                values (?, ?, ?)
                returning id
                """,
            UUID.class,
            label + "-" + UUID.randomUUID() + "@example.com",
            "hashed-password",
            displayName
        );
    }

    private AutoModerationSnapshot snapshot(
        UUID policyId,
        AutoModerationExecutionMode executionMode,
        Instant evaluatedAt
    ) {
        return new AutoModerationSnapshot(
            policyId,
            executionMode,
            10,
            AutoModerationDecisionType.APPROVE,
            List.of(),
            null,
            CommentStatus.APPROVED,
            evaluatedAt
        );
    }

    private Integer decisionEventCount(UUID commentId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from automod_decision_events where comment_id = ?",
            Integer.class,
            commentId
        );
    }

    private UUID insertSite(
        UUID ownerId,
        String domain,
        String origin,
        ModerationMode moderationMode,
        boolean active
    ) {
        UUID siteId = jdbcTemplate.queryForObject(
            """
                insert into sites (owner_id, name, domain, public_key, moderation_mode, is_active)
                values (?, ?, ?, ?, ?, ?)
                returning id
                """,
            UUID.class,
            ownerId,
            domain,
            domain,
            UUID.randomUUID().toString().replace("-", "").repeat(2),
            moderationMode.name(),
            active
        );
        jdbcTemplate.update(
            """
                insert into site_allowed_origins (site_id, origin)
                values (?, ?)
                """,
            siteId,
            origin
        );
        return siteId;
    }
}
