package com.cloudcomment.site.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SiteWidgetHealthRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SiteWidgetHealthRepository repository;

    @Test
    void recordsOnlyCurrentOriginsAndMasksMissingSites() {
        UUID siteId = insertSite();
        Instant successAt = Instant.parse("2026-07-12T10:00:00Z");
        Instant rejectedAt = Instant.parse("2026-07-12T11:00:00Z");

        repository.recordSuccessfulOrigin(siteId, "https://example.com", successAt);
        repository.recordRejectedOrigin(siteId, "https://blocked.example.com:8443", rejectedAt);
        repository.recordSuccessfulOrigin(siteId, "https://older.example.com", successAt.minusSeconds(1));
        repository.recordRejectedOrigin(siteId, "https://older-blocked.example.com", rejectedAt.minusSeconds(1));
        repository.recordRejectedOrigin(UUID.randomUUID(), "https://missing.example.com", rejectedAt);

        var health = repository.findBySiteId(siteId).orElseThrow();
        assertThat(health.lastSuccessfulOrigin()).isEqualTo("https://example.com");
        assertThat(health.lastSuccessfulAt()).isEqualTo(successAt);
        assertThat(health.lastRejectedOrigin()).isEqualTo("https://blocked.example.com:8443");
        assertThat(health.lastRejectedAt()).isEqualTo(rejectedAt);
    }

    @Test
    void detectsFirstCommentAndClearsOnlyExpiredRejectedDetails() {
        UUID siteId = insertSite();
        UUID pageId = jdbcTemplate.queryForObject(
            "insert into pages (site_id, url) values (?, ?) returning id",
            UUID.class,
            siteId,
            "https://example.com/page?private=query"
        );
        jdbcTemplate.update(
            "insert into comments (page_id, author_email, body, status) values (?, ?, ?, 'PENDING')",
            pageId,
            "author@example.com",
            "Комментарий"
        );
        Instant threshold = Instant.parse("2026-06-12T12:00:00Z");
        repository.recordRejectedOrigin(siteId, "https://example.com", threshold.minusSeconds(1));

        assertThat(repository.hasComments(siteId)).isTrue();
        assertThat(repository.clearRejectedBefore(threshold)).isOne();
        var health = repository.findBySiteId(siteId).orElseThrow();
        assertThat(health.lastRejectedOrigin()).isNull();
        assertThat(health.lastRejectedAt()).isNull();
    }

    private UUID insertSite() {
        UUID ownerId = jdbcTemplate.queryForObject(
            "insert into app_users (email, password_hash) values (?, ?) returning id",
            UUID.class,
            UUID.randomUUID() + "@example.com",
            "hash"
        );
        return jdbcTemplate.queryForObject(
            "insert into sites (owner_id, name, domain, public_key) values (?, ?, ?, ?) returning id",
            UUID.class,
            ownerId,
            "Health site",
            UUID.randomUUID() + ".example.com",
            "key-" + UUID.randomUUID()
        );
    }
}
