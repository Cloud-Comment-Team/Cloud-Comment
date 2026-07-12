package com.cloudcomment.notification.persistence;

import com.cloudcomment.notification.domain.OwnerNotification;
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
class OwnerNotificationRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OwnerNotificationRepository repository;

    @Test
    void persistsDeduplicatesReadsAndIsolatesOwnerNotifications() {
        UUID ownerId = insertUser("owner");
        UUID foreignOwnerId = insertUser("foreign-owner");
        UUID siteId = insertSite(ownerId, "Исходный сайт");
        UUID pageId = insertPage(siteId, "https://example.com/original");
        UUID commentId = insertComment(pageId, "Исходный текст");
        Instant createdAt = Instant.parse("2026-07-12T12:00:00Z");

        OwnerNotification first = repository.create(ownerId, commentId, "comment-created:" + commentId, createdAt);
        OwnerNotification repeated = repository.create(ownerId, commentId, "comment-created:" + commentId, createdAt);
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repository.countUnreadByOwnerId(ownerId)).isOne();
        assertThat(repository.countUnreadByOwnerId(foreignOwnerId)).isZero();
        assertThat(repository.findByOwnerId(foreignOwnerId, 1, 20).items()).isEmpty();

        jdbcTemplate.update("update sites set name = ? where id = ?", "Обновлённый сайт", siteId);
        jdbcTemplate.update("update pages set url = ? where id = ?", "https://example.com/updated", pageId);
        jdbcTemplate.update("update comments set body = ? where id = ?", "Обновлённый текст", commentId);
        var currentView = repository.findByOwnerId(ownerId, 1, 20).items().getFirst();
        assertThat(currentView.siteName()).isEqualTo("Обновлённый сайт");
        assertThat(currentView.pageUrl()).isEqualTo("https://example.com/updated");
        assertThat(currentView.content()).isEqualTo("Обновлённый текст");

        assertThat(repository.markRead(foreignOwnerId, first.id(), createdAt.plusSeconds(60))).isEmpty();
        assertThat(repository.markRead(ownerId, first.id(), createdAt.plusSeconds(60)).orElseThrow().readAt())
            .isEqualTo(createdAt.plusSeconds(60));
        assertThat(repository.countUnreadByOwnerId(ownerId)).isZero();
    }

    @Test
    void marksAllAndDeletesOnlyExpiredNotifications() {
        UUID ownerId = insertUser("retention-owner");
        UUID siteId = insertSite(ownerId, "Retention");
        UUID pageId = insertPage(siteId, "https://example.com/retention");
        UUID oldCommentId = insertComment(pageId, "Старое уведомление");
        UUID freshCommentId = insertComment(pageId, "Свежее уведомление");
        Instant threshold = Instant.parse("2026-04-13T00:00:00Z");
        repository.create(ownerId, oldCommentId, "old", threshold.minusSeconds(1));
        repository.create(ownerId, freshCommentId, "fresh", threshold);

        assertThat(repository.markAllRead(ownerId, threshold.plusSeconds(10))).isEqualTo(2);
        assertThat(repository.countUnreadByOwnerId(ownerId)).isZero();
        assertThat(repository.deleteCreatedBefore(threshold)).isOne();
        assertThat(repository.findByOwnerId(ownerId, 1, 20).items())
            .singleElement().satisfies(view -> assertThat(view.commentId()).isEqualTo(freshCommentId));
    }

    private UUID insertUser(String label) {
        return jdbcTemplate.queryForObject(
            "insert into app_users (email, password_hash) values (?, ?) returning id",
            UUID.class,
            label + "-" + UUID.randomUUID() + "@example.com",
            "hash"
        );
    }

    private UUID insertSite(UUID ownerId, String name) {
        return jdbcTemplate.queryForObject(
            "insert into sites (owner_id, name, domain, public_key) values (?, ?, ?, ?) returning id",
            UUID.class,
            ownerId,
            name,
            UUID.randomUUID() + ".example.com",
            "key-" + UUID.randomUUID()
        );
    }

    private UUID insertPage(UUID siteId, String url) {
        return jdbcTemplate.queryForObject(
            "insert into pages (site_id, url) values (?, ?) returning id",
            UUID.class,
            siteId,
            url
        );
    }

    private UUID insertComment(UUID pageId, String body) {
        return jdbcTemplate.queryForObject(
            """
                insert into comments (page_id, author_email, body, status, created_at)
                values (?, ?, ?, 'PENDING', ?)
                returning id
                """,
            UUID.class,
            pageId,
            "author@example.com",
            body,
            OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
