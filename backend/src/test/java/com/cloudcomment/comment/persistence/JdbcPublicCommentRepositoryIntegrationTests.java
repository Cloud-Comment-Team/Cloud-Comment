package com.cloudcomment.comment.persistence;

import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.site.domain.ModerationMode;
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
class JdbcPublicCommentRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PublicCommentRepository repository;

    @Test
    void resolvesActiveSiteAllowedOriginsAndCreatesPageCommentsTransactionally() {
        UUID ownerId = insertUser("owner", "Owner Name");
        UUID visitorId = insertUser("visitor", "Visitor Name");
        UUID siteId = insertSite(ownerId, "example.com", "https://example.com", ModerationMode.PRE_MODERATION, true);
        UUID inactiveSiteId = insertSite(ownerId, "inactive.example.com", "https://inactive.example.com", ModerationMode.POST_MODERATION, false);
        String pageUrl = "https://example.com/blog/post-1";

        assertThat(repository.findActiveSite(siteId))
            .hasValueSatisfying(site -> {
                assertThat(site.id()).isEqualTo(siteId);
                assertThat(site.moderationMode()).isEqualTo(ModerationMode.PRE_MODERATION);
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
            approvedRoot.id(),
            visitorId,
            "Visitor Name",
            "visitor@example.com",
            "Pending reply",
            CommentStatus.PENDING
        );

        CommentPage comments = repository.findApprovedComments(siteId, pageId, 1, 20);

        assertThat(comments.totalItems()).isEqualTo(1);
        assertThat(comments.items()).singleElement().satisfies(root -> {
            assertThat(root.id()).isEqualTo(approvedRoot.id());
            assertThat(root.content()).isEqualTo("Approved root");
            assertThat(root.author().id()).isEqualTo(visitorId);
            assertThat(root.author().email()).isEqualTo("visitor@example.com");
            assertThat(root.author().displayName()).isEqualTo("Visitor Name");
            assertThat(root.status()).isEqualTo(CommentStatus.APPROVED);
            assertThat(root.replies()).singleElement().satisfies(reply -> {
                assertThat(reply.id()).isEqualTo(approvedReply.id());
                assertThat(reply.content()).isEqualTo("Approved reply");
            });
        });

        assertThat(repository.existsApprovedRootCommentOnPage(pageId, approvedRoot.id())).isTrue();
        assertThat(repository.existsApprovedRootCommentOnPage(pageId, approvedReply.id())).isFalse();
        assertThat(repository.existsApprovedRootCommentOnPage(pageId, pendingRoot.id())).isFalse();
        assertThat(repository.existsApprovedRootCommentOnPage(pageId, UUID.randomUUID())).isFalse();
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
            "a".repeat(63) + (active ? "1" : "2"),
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
