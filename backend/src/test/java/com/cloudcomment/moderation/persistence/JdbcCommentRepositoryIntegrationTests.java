package com.cloudcomment.moderation.persistence;

import com.cloudcomment.moderation.application.ModerationCommentFilters;
import com.cloudcomment.moderation.application.ModerationCommentPage;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentSortField;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationActionType;
import com.cloudcomment.moderation.domain.SortOrder;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcCommentRepositoryIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ModerationActionRepository moderationActionRepository;

    @Test
    void findByOwnerIdFiltersBySiteStatusPageUrlDateRangeSearchAndPaginates() {
        UUID ownerId = insertUser("owner");
        UUID otherOwnerId = insertUser("other");
        UUID ownerSiteId = insertSite(ownerId, "owner-site");
        UUID otherSiteId = insertSite(otherOwnerId, "other-site");
        UUID ownerPageId = insertPage(ownerSiteId, "https://example.com/blog/post-1", "Post 1");
        UUID otherPageId = insertPage(otherSiteId, "https://other.example.com/page", "Other page");
        UUID secondOwnerPageId = insertPage(ownerSiteId, "https://example.com/blog/post-2", "Post 2");

        UUID pendingCommentId = insertComment(ownerPageId, "Pending comment about widgets", "PENDING", Instant.parse("2026-06-28T10:00:00Z"));
        UUID approvedCommentId = insertComment(ownerPageId, "Approved comment about widgets", "APPROVED", Instant.parse("2026-06-28T11:00:00Z"));
        insertComment(secondOwnerPageId, "Second page comment", "PENDING", Instant.parse("2026-06-28T12:00:00Z"));
        insertComment(otherPageId, "Foreign comment", "PENDING", Instant.parse("2026-06-28T13:00:00Z"));

        ModerationCommentPage siteFiltered = commentRepository.findByOwnerId(
            ownerId,
            new ModerationCommentFilters(
                ownerSiteId,
                null,
                null,
                null,
                null,
                null,
                null,
                CommentSortField.CREATED_AT,
                SortOrder.ASC
            ),
            1,
            20
        );
        assertThat(siteFiltered.items()).hasSize(3);
        assertThat(siteFiltered.totalItems()).isEqualTo(3);

        ModerationCommentPage statusFiltered = commentRepository.findByOwnerId(
            ownerId,
            new ModerationCommentFilters(
                ownerSiteId,
                null,
                null,
                CommentStatus.APPROVED,
                null,
                null,
                null,
                CommentSortField.CREATED_AT,
                SortOrder.ASC
            ),
            1,
            20
        );
        assertThat(statusFiltered.items()).extracting(Comment::id).containsExactly(approvedCommentId);

        ModerationCommentPage pageUrlFiltered = commentRepository.findByOwnerId(
            ownerId,
            new ModerationCommentFilters(
                null,
                null,
                "https://example.com/blog/post-1",
                null,
                null,
                null,
                null,
                CommentSortField.CREATED_AT,
                SortOrder.ASC
            ),
            1,
            20
        );
        assertThat(pageUrlFiltered.items()).hasSize(2);

        ModerationCommentPage pageIdFiltered = commentRepository.findByOwnerId(
            ownerId,
            new ModerationCommentFilters(
                null,
                ownerPageId,
                null,
                null,
                null,
                null,
                null,
                CommentSortField.CREATED_AT,
                SortOrder.ASC
            ),
            1,
            20
        );
        assertThat(pageIdFiltered.items()).hasSize(2);

        ModerationCommentPage dateFiltered = commentRepository.findByOwnerId(
            ownerId,
            new ModerationCommentFilters(
                ownerSiteId,
                null,
                null,
                null,
                Instant.parse("2026-06-28T10:30:00Z"),
                Instant.parse("2026-06-28T11:30:00Z"),
                null,
                CommentSortField.CREATED_AT,
                SortOrder.ASC
            ),
            1,
            20
        );
        assertThat(dateFiltered.items()).extracting(Comment::id).containsExactly(approvedCommentId);

        ModerationCommentPage searchFiltered = commentRepository.findByOwnerId(
            ownerId,
            new ModerationCommentFilters(
                ownerSiteId,
                null,
                null,
                null,
                null,
                null,
                "widget",
                CommentSortField.CREATED_AT,
                SortOrder.ASC
            ),
            1,
            20
        );
        assertThat(searchFiltered.items()).hasSize(2);

        ModerationCommentPage firstPage = commentRepository.findByOwnerId(
            ownerId,
            new ModerationCommentFilters(
                ownerSiteId,
                null,
                null,
                null,
                null,
                null,
                null,
                CommentSortField.CREATED_AT,
                SortOrder.ASC
            ),
            1,
            2
        );
        assertThat(firstPage.items()).extracting(Comment::id).containsExactly(pendingCommentId, approvedCommentId);
        assertThat(firstPage.totalItems()).isEqualTo(3);
        assertThat(firstPage.page()).isEqualTo(1);
        assertThat(firstPage.pageSize()).isEqualTo(2);

        ModerationCommentPage secondPage = commentRepository.findByOwnerId(
            ownerId,
            new ModerationCommentFilters(
                ownerSiteId,
                null,
                null,
                null,
                null,
                null,
                null,
                CommentSortField.CREATED_AT,
                SortOrder.ASC
            ),
            2,
            2
        );
        assertThat(secondPage.items()).hasSize(1);
    }

    @Test
    void updateStatusAndCreateModerationActionPersistChanges() {
        UUID ownerId = insertUser("owner");
        UUID siteId = insertSite(ownerId, "site");
        UUID pageId = insertPage(siteId, "https://example.com/page", "Page");
        UUID commentId = insertComment(pageId, "Needs review", "PENDING", Instant.parse("2026-06-28T10:00:00Z"));

        Comment updated = commentRepository.updateStatus(commentId, CommentStatus.APPROVED, "Looks good").orElseThrow();
        assertThat(updated.status()).isEqualTo(CommentStatus.APPROVED);

        var action = moderationActionRepository.create(
            commentId,
            ownerId,
            ModerationActionType.APPROVE,
            CommentStatus.PENDING,
            CommentStatus.APPROVED,
            "Looks good"
        );
        assertThat(action.commentId()).isEqualTo(commentId);
        assertThat(action.moderatorEmail()).isEqualTo(jdbcTemplate.queryForObject(
            "select email from app_users where id = ?",
            String.class,
            ownerId
        ));

        Long actionCount = jdbcTemplate.queryForObject(
            "select count(*) from moderation_actions where comment_id = ?",
            Long.class,
            commentId
        );
        assertThat(actionCount).isEqualTo(1);
    }

    private UUID insertUser(String label) {
        return jdbcTemplate.queryForObject(
            """
                insert into app_users (email, password_hash)
                values (?, ?)
                returning id
                """,
            UUID.class,
            label + "-" + UUID.randomUUID() + "@example.com",
            "hashed-password"
        );
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

    private UUID insertPage(UUID siteId, String url, String title) {
        return jdbcTemplate.queryForObject(
            """
                insert into pages (site_id, url, title)
                values (?, ?, ?)
                returning id
                """,
            UUID.class,
            siteId,
            url,
            title
        );
    }

    private UUID insertComment(UUID pageId, String body, String status, Instant createdAt) {
        OffsetDateTime timestamp = createdAt.atOffset(ZoneOffset.UTC);
        return jdbcTemplate.queryForObject(
            """
                insert into comments (page_id, body, status, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                returning id
                """,
            UUID.class,
            pageId,
            body,
            status,
            timestamp,
            timestamp
        );
    }
}
