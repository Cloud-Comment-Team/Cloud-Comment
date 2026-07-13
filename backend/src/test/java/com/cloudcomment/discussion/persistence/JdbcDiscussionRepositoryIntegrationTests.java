package com.cloudcomment.discussion.persistence;

import com.cloudcomment.discussion.application.DiscussionFilters;
import com.cloudcomment.discussion.application.DiscussionPage;
import com.cloudcomment.discussion.domain.DiscussionFilter;
import com.cloudcomment.discussion.domain.DiscussionStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcDiscussionRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DiscussionRepository discussionRepository;

    @Test
    void listsOnlyOwnerApprovedRootsAndSupportsStableViews() {
        UUID ownerId = insertUser("owner", "Владелец");
        UUID visitorId = insertUser("visitor", "Анна");
        UUID foreignOwnerId = insertUser("foreign", "Другой владелец");
        UUID siteId = insertSite(ownerId, "Редакция");
        UUID foreignSiteId = insertSite(foreignOwnerId, "Чужой сайт");
        UUID pageId = insertPage(siteId, "https://example.com/article", "Новая статья");
        UUID foreignPageId = insertPage(foreignSiteId, "https://foreign.example/page", "Чужая статья");

        UUID rootId = insertComment(pageId, null, null, "mail@example.com", null,
            "Первый комментарий", "APPROVED", Instant.parse("2026-07-13T10:00:00Z"));
        UUID replyId = insertComment(pageId, rootId, visitorId, null, null,
            "Ответ про продукт", "APPROVED", Instant.parse("2026-07-13T10:05:00Z"));
        insertComment(pageId, rootId, visitorId, null, null,
            "Более поздняя реплика", "APPROVED", Instant.parse("2026-07-13T10:07:00Z"));
        insertNotification(ownerId, replyId);

        UUID answeredRootId = insertComment(pageId, null, visitorId, null, null,
            "Вопрос владельцу", "APPROVED", Instant.parse("2026-07-13T09:00:00Z"));
        insertComment(pageId, answeredRootId, ownerId, null, null,
            "Ответ владельца", "APPROVED", Instant.parse("2026-07-13T09:05:00Z"));

        insertComment(pageId, null, visitorId, null, null,
            "На модерации", "PENDING", Instant.parse("2026-07-13T11:00:00Z"));
        insertComment(foreignPageId, null, visitorId, null, null,
            "Чужое обсуждение", "APPROVED", Instant.parse("2026-07-13T12:00:00Z"));

        DiscussionPage all = discussionRepository.findByOwnerId(
            ownerId,
            new DiscussionFilters(null, DiscussionFilter.ALL, null),
            1,
            20
        );
        assertThat(all.totalItems()).isEqualTo(2);
        assertThat(all.items()).extracting(item -> item.rootCommentId()).containsExactly(rootId, answeredRootId);
        assertThat(all.items().getFirst().lastAuthor().displayName()).isEqualTo("Анна");
        assertThat(all.items().getFirst().lastAuthor().owner()).isFalse();
        assertThat(all.items().getFirst().replyCount()).isEqualTo(2);
        assertThat(all.items().getFirst().unread()).isTrue();
        assertThat(all.items().getFirst().status()).isEqualTo(DiscussionStatus.NEEDS_REPLY);
        assertThat(all.items().getLast().status()).isEqualTo(DiscussionStatus.ACTIVE);
        assertThat(all.items().getLast().lastAuthor().owner()).isTrue();

        DiscussionPage unread = discussionRepository.findByOwnerId(
            ownerId,
            new DiscussionFilters(siteId, DiscussionFilter.UNREAD, "продукт"),
            1,
            20
        );
        assertThat(unread.items()).singleElement().satisfies(item -> {
            assertThat(item.rootCommentId()).isEqualTo(rootId);
            assertThat(item.pageTitle()).isEqualTo("Новая статья");
        });

        DiscussionPage needsReply = discussionRepository.findByOwnerId(
            ownerId,
            new DiscussionFilters(null, DiscussionFilter.NEEDS_REPLY, null),
            1,
            20
        );
        assertThat(needsReply.items()).extracting(item -> item.rootCommentId()).containsExactly(rootId);
    }

    @Test
    void loadsOwnerScopedThreadAndNeverExposesEmailAsDisplayName() {
        UUID ownerId = insertUser("owner-thread", "Владелец");
        UUID foreignOwnerId = insertUser("foreign-thread", "Другой владелец");
        UUID siteId = insertSite(ownerId, "Медиа");
        UUID foreignSiteId = insertSite(foreignOwnerId, "Чужое медиа");
        UUID pageId = insertPage(siteId, "https://example.com/thread", null);
        UUID foreignPageId = insertPage(foreignSiteId, "https://foreign.example/thread", null);
        UUID rootId = insertComment(pageId, null, null, "guest@example.com", "guest@example.com",
            "Корневое сообщение", "APPROVED", Instant.parse("2026-07-13T10:00:00Z"));
        insertComment(pageId, rootId, ownerId, null, null,
            "Ответ владельца", "APPROVED", Instant.parse("2026-07-13T10:05:00Z"));
        UUID foreignRootId = insertComment(foreignPageId, null, null, "foreign@example.com", "Чужой",
            "Чужое сообщение", "APPROVED", Instant.parse("2026-07-13T10:10:00Z"));

        var thread = discussionRepository.findThreadByOwnerId(ownerId, rootId).orElseThrow();
        assertThat(thread.messages()).hasSize(2);
        assertThat(thread.messages().getFirst().author().displayName()).isEqualTo("Участник");
        assertThat(thread.messages().getFirst().author().owner()).isFalse();
        assertThat(thread.messages().getLast().author().displayName()).isEqualTo("Владелец");
        assertThat(thread.messages().getLast().author().owner()).isTrue();
        assertThat(discussionRepository.findThreadByOwnerId(ownerId, foreignRootId)).isEmpty();
    }

    @Test
    void createsOwnerReplyOnceAndRejectsForeignThread() {
        UUID ownerId = insertUser("reply-owner", "Редактор");
        UUID visitorId = insertUser("reply-visitor", "Анна");
        UUID foreignOwnerId = insertUser("reply-foreign", "Другой владелец");
        UUID siteId = insertSite(ownerId, "Редакция");
        UUID foreignSiteId = insertSite(foreignOwnerId, "Чужая редакция");
        UUID pageId = insertPage(siteId, "https://example.com/reply", "Ответы");
        UUID foreignPageId = insertPage(foreignSiteId, "https://foreign.example/reply", "Чужие ответы");
        UUID rootId = insertComment(pageId, null, visitorId, null, "Анна",
            "Можно уточнить?", "APPROVED", Instant.parse("2026-07-13T10:00:00Z"));
        UUID foreignRootId = insertComment(foreignPageId, null, visitorId, null, "Анна",
            "Чужой вопрос", "APPROVED", Instant.parse("2026-07-13T10:01:00Z"));
        UUID operationId = UUID.randomUUID();

        var created = discussionRepository.createOwnerReply(
            ownerId, rootId, operationId, "  Да, конечно.  "
        ).orElseThrow();
        var replayed = discussionRepository.createOwnerReply(
            ownerId, rootId, operationId, "Да, конечно."
        ).orElseThrow();

        assertThat(created.created()).isTrue();
        assertThat(replayed.created()).isFalse();
        assertThat(replayed.message().id()).isEqualTo(created.message().id());
        assertThat(created.message().author().owner()).isTrue();
        assertThat(created.message().author().displayName()).isEqualTo("Редактор");
        assertThat(created.message().content()).isEqualTo("  Да, конечно.  ");
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from comments where owner_reply_operation_id = ?", Integer.class, operationId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select author_kind from comments where id = ?", String.class, created.message().id()
        )).isEqualTo("OWNER");
        assertThat(discussionRepository.createOwnerReply(
            ownerId, foreignRootId, UUID.randomUUID(), "Недоступный ответ"
        )).isEmpty();
    }

    private UUID insertUser(String suffix, String displayName) {
        return jdbcTemplate.queryForObject(
            "insert into app_users (email, password_hash, display_name) values (?, 'hash', ?) returning id",
            UUID.class,
            suffix + "-" + UUID.randomUUID() + "@example.com",
            displayName
        );
    }

    private UUID insertSite(UUID ownerId, String name) {
        String suffix = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject(
            """
                insert into sites (owner_id, name, domain, public_key)
                values (?, ?, ?, ?) returning id
                """,
            UUID.class,
            ownerId,
            name,
            suffix + ".example.com",
            suffix.replace("-", "")
        );
    }

    private UUID insertPage(UUID siteId, String url, String title) {
        return jdbcTemplate.queryForObject(
            "insert into pages (site_id, url, title) values (?, ?, ?) returning id",
            UUID.class,
            siteId,
            url,
            title
        );
    }

    private UUID insertComment(
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorEmail,
        String authorName,
        String body,
        String status,
        Instant createdAt
    ) {
        return jdbcTemplate.queryForObject(
            """
                insert into comments (
                    page_id, parent_id, author_user_id, author_email, author_name, body, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                """,
            UUID.class,
            pageId,
            parentId,
            authorUserId,
            authorEmail,
            authorName,
            body,
            status,
            createdAt.atOffset(ZoneOffset.UTC),
            createdAt.atOffset(ZoneOffset.UTC)
        );
    }

    private void insertNotification(UUID ownerId, UUID commentId) {
        jdbcTemplate.update(
            """
                insert into owner_notifications (owner_id, comment_id, deduplication_key, created_at)
                values (?, ?, ?, ?)
                """,
            ownerId,
            commentId,
            "discussion:" + commentId,
            OffsetDateTime.ofInstant(Instant.parse("2026-07-13T10:06:00Z"), ZoneOffset.UTC)
        );
    }
}
