package com.cloudcomment.access.persistence;

import com.cloudcomment.access.domain.OwnedResourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcResourceOwnershipRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ResourceOwnershipRepository resourceOwnershipRepository;

    @Test
    void ownerCanAccessResourcesThroughSiteOwnershipAndOtherUsersCannot() {
        UUID ownerId = insertUser("owner");
        UUID otherUserId = insertUser("other");
        UUID siteId = insertSite(ownerId);
        UUID originId = insertAllowedOrigin(siteId);
        UUID pageId = insertPage(siteId);
        UUID commentId = insertComment(pageId, otherUserId);
        UUID moderationActionId = insertModerationAction(commentId, otherUserId);

        assertOwned(ownerId, OwnedResourceType.SITE, siteId);
        assertOwned(ownerId, OwnedResourceType.SITE_ALLOWED_ORIGIN, originId);
        assertOwned(ownerId, OwnedResourceType.PAGE, pageId);
        assertOwned(ownerId, OwnedResourceType.COMMENT, commentId);
        assertOwned(ownerId, OwnedResourceType.MODERATION_ACTION, moderationActionId);

        assertNotOwned(otherUserId, OwnedResourceType.SITE, siteId);
        assertNotOwned(otherUserId, OwnedResourceType.SITE_ALLOWED_ORIGIN, originId);
        assertNotOwned(otherUserId, OwnedResourceType.PAGE, pageId);
        assertNotOwned(otherUserId, OwnedResourceType.COMMENT, commentId);
        assertNotOwned(otherUserId, OwnedResourceType.MODERATION_ACTION, moderationActionId);
    }

    @Test
    void randomResourceIdsAreNotOwned() {
        UUID ownerId = insertUser("missing-owner");

        assertNotOwned(ownerId, OwnedResourceType.SITE, UUID.randomUUID());
        assertNotOwned(ownerId, OwnedResourceType.SITE_ALLOWED_ORIGIN, UUID.randomUUID());
        assertNotOwned(ownerId, OwnedResourceType.PAGE, UUID.randomUUID());
        assertNotOwned(ownerId, OwnedResourceType.COMMENT, UUID.randomUUID());
        assertNotOwned(ownerId, OwnedResourceType.MODERATION_ACTION, UUID.randomUUID());
    }

    private void assertOwned(UUID ownerId, OwnedResourceType resourceType, UUID resourceId) {
        assertThat(resourceOwnershipRepository.isOwnedBy(ownerId, resourceType, resourceId)).isTrue();
    }

    private void assertNotOwned(UUID ownerId, OwnedResourceType resourceType, UUID resourceId) {
        assertThat(resourceOwnershipRepository.isOwnedBy(ownerId, resourceType, resourceId)).isFalse();
    }

    private UUID insertUser(String label) {
        String suffix = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject(
            """
                insert into app_users (email, password_hash)
                values (?, ?)
                returning id
                """,
            UUID.class,
            label + "-" + suffix + "@example.com",
            "hashed-password"
        );
    }

    private UUID insertSite(UUID ownerId) {
        String suffix = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject(
            """
                insert into sites (owner_id, name, domain, public_key)
                values (?, ?, ?, ?)
                returning id
                """,
            UUID.class,
            ownerId,
            "Site " + suffix,
            suffix + ".example.com",
            suffix.replace("-", "")
        );
    }

    private UUID insertAllowedOrigin(UUID siteId) {
        String suffix = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject(
            """
                insert into site_allowed_origins (site_id, origin)
                values (?, ?)
                returning id
                """,
            UUID.class,
            siteId,
            "https://" + suffix + ".example.com"
        );
    }

    private UUID insertPage(UUID siteId) {
        String suffix = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject(
            """
                insert into pages (site_id, url, title)
                values (?, ?, ?)
                returning id
                """,
            UUID.class,
            siteId,
            "https://page.example.com/" + suffix,
            "Page " + suffix
        );
    }

    private UUID insertComment(UUID pageId, UUID authorUserId) {
        return jdbcTemplate.queryForObject(
            """
                insert into comments (page_id, author_user_id, body, status)
                values (?, ?, ?, ?)
                returning id
                """,
            UUID.class,
            pageId,
            authorUserId,
            "Comment body",
            "PENDING"
        );
    }

    private UUID insertModerationAction(UUID commentId, UUID moderatorId) {
        return jdbcTemplate.queryForObject(
            """
                insert into moderation_actions (comment_id, moderator_id, action, from_status, to_status)
                values (?, ?, ?, ?, ?)
                returning id
                """,
            UUID.class,
            commentId,
            moderatorId,
            "APPROVE",
            "PENDING",
            "APPROVED"
        );
    }
}
