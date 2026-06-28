package com.cloudcomment.comment.persistence;

import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.WidgetSite;
import com.cloudcomment.comment.domain.CommentAuthor;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.site.domain.ModerationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcPublicCommentRepository implements PublicCommentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Optional<WidgetSite> findActiveSite(UUID siteId) {
        List<WidgetSite> sites = jdbcTemplate.query(
            """
                select id, moderation_mode
                from sites
                where id = ? and is_active = true
                """,
            (resultSet, rowNumber) -> new WidgetSite(
                resultSet.getObject("id", UUID.class),
                ModerationMode.valueOf(resultSet.getString("moderation_mode"))
            ),
            siteId
        );
        return sites.stream().findFirst();
    }

    @Override
    public boolean isAllowedOrigin(UUID siteId, String normalizedOrigin) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from site_allowed_origins
                    where site_id = ? and origin = ?
                )
                """,
            Boolean.class,
            siteId,
            normalizedOrigin
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<UUID> findPageId(UUID siteId, String pageUrl) {
        List<UUID> pageIds = jdbcTemplate.queryForList(
            """
                select id
                from pages
                where site_id = ? and url = ?
                """,
            UUID.class,
            siteId,
            pageUrl
        );
        return pageIds.stream().findFirst();
    }

    @Override
    @Transactional
    public UUID findOrCreatePage(UUID siteId, String pageUrl) {
        UUID pageId = jdbcTemplate.queryForObject(
            """
                with inserted as (
                    insert into pages (site_id, url)
                    values (?, ?)
                    on conflict (site_id, url) do nothing
                    returning id
                )
                select id from inserted
                union all
                select id from pages where site_id = ? and url = ?
                limit 1
                """,
            UUID.class,
            siteId,
            pageUrl,
            siteId,
            pageUrl
        );
        return Objects.requireNonNull(pageId, "page id must not be null");
    }

    @Override
    public CommentPage findApprovedComments(UUID siteId, UUID pageId, int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        List<CommentRow> rootRows = jdbcTemplate.query(
            """
                select c.id,
                       p.site_id,
                       c.page_id,
                       c.parent_id,
                       c.author_user_id,
                       coalesce(c.author_email, u.email) as author_email,
                       coalesce(c.author_name, nullif(u.display_name, ''), c.author_email, u.email) as author_name,
                       c.body,
                       c.status,
                       c.created_at,
                       c.updated_at
                from comments c
                join pages p on p.id = c.page_id
                left join app_users u on u.id = c.author_user_id
                where p.site_id = ?
                  and c.page_id = ?
                  and c.status = 'APPROVED'
                  and c.parent_id is null
                order by c.created_at asc, c.id asc
                limit ? offset ?
                """,
            this::mapCommentRow,
            siteId,
            pageId,
            pageSize,
            offset
        );

        Long totalItems = jdbcTemplate.queryForObject(
            """
                select count(*)
                from comments
                where page_id = ?
                  and status = 'APPROVED'
                  and parent_id is null
                """,
            Long.class,
            pageId
        );

        return new CommentPage(buildThreads(rootRows, findApprovedReplies(rootRows)), page, pageSize, totalItemsOrZero(totalItems));
    }

    @Override
    public boolean existsApprovedCommentOnPage(UUID pageId, UUID commentId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from comments
                    where id = ? and page_id = ? and status = 'APPROVED'
                )
                """,
            Boolean.class,
            commentId,
            pageId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    @Transactional
    public CommentView createComment(
        UUID siteId,
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorName,
        String authorEmail,
        String content,
        CommentStatus status
    ) {
        CommentRow row = jdbcTemplate.queryForObject(
            """
                insert into comments (
                    page_id,
                    parent_id,
                    author_user_id,
                    author_name,
                    author_email,
                    body,
                    status
                )
                values (?, ?, ?, ?, ?, ?, ?)
                returning id,
                          ?::uuid as site_id,
                          page_id,
                          parent_id,
                          author_user_id,
                          author_email,
                          author_name,
                          body,
                          status,
                          created_at,
                          updated_at
                """,
            this::mapCommentRow,
            pageId,
            parentId,
            authorUserId,
            authorName,
            authorEmail,
            content,
            status.name(),
            siteId
        );
        return toCommentView(Objects.requireNonNull(row, "created comment must not be null"), List.of());
    }

    private List<CommentRow> findApprovedReplies(List<CommentRow> rootRows) {
        List<UUID> rootIds = rootRows.stream().map(CommentRow::id).toList();
        if (rootIds.isEmpty()) {
            return List.of();
        }

        return namedParameterJdbcTemplate.query(
            """
                with recursive thread as (
                    select c.id,
                           p.site_id,
                           c.page_id,
                           c.parent_id,
                           c.author_user_id,
                           coalesce(c.author_email, u.email) as author_email,
                           coalesce(c.author_name, nullif(u.display_name, ''), c.author_email, u.email) as author_name,
                           c.body,
                           c.status,
                           c.created_at,
                           c.updated_at,
                           1 as depth
                    from comments c
                    join pages p on p.id = c.page_id
                    left join app_users u on u.id = c.author_user_id
                    where c.parent_id in (:rootIds)
                      and c.status = 'APPROVED'

                    union all

                    select child.id,
                           p.site_id,
                           child.page_id,
                           child.parent_id,
                           child.author_user_id,
                           coalesce(child.author_email, u.email) as author_email,
                           coalesce(child.author_name, nullif(u.display_name, ''), child.author_email, u.email) as author_name,
                           child.body,
                           child.status,
                           child.created_at,
                           child.updated_at,
                           thread.depth + 1 as depth
                    from comments child
                    join thread on thread.id = child.parent_id
                    join pages p on p.id = child.page_id
                    left join app_users u on u.id = child.author_user_id
                    where child.status = 'APPROVED'
                )
                select id,
                       site_id,
                       page_id,
                       parent_id,
                       author_user_id,
                       author_email,
                       author_name,
                       body,
                       status,
                       created_at,
                       updated_at
                from thread
                order by depth asc, created_at asc, id asc
                """,
            new MapSqlParameterSource("rootIds", rootIds),
            this::mapCommentRow
        );
    }

    private List<CommentView> buildThreads(List<CommentRow> rootRows, List<CommentRow> replyRows) {
        Map<UUID, MutableComment> comments = new LinkedHashMap<>();
        for (CommentRow row : rootRows) {
            comments.put(row.id(), new MutableComment(row));
        }
        for (CommentRow row : replyRows) {
            MutableComment comment = new MutableComment(row);
            comments.put(row.id(), comment);
            MutableComment parent = comments.get(row.parentId());
            if (parent != null) {
                parent.replies().add(comment);
            }
        }
        return rootRows.stream()
            .map(row -> comments.get(row.id()).toView())
            .toList();
    }

    private CommentView toCommentView(CommentRow row, List<CommentView> replies) {
        return new CommentView(
            row.id(),
            row.siteId(),
            row.pageId(),
            row.parentId(),
            new CommentAuthor(row.authorUserId(), row.authorEmail(), row.authorName()),
            row.body(),
            row.status(),
            row.createdAt(),
            row.updatedAt(),
            replies
        );
    }

    private CommentRow mapCommentRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CommentRow(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("site_id", UUID.class),
            resultSet.getObject("page_id", UUID.class),
            resultSet.getObject("parent_id", UUID.class),
            resultSet.getObject("author_user_id", UUID.class),
            resultSet.getString("author_email"),
            resultSet.getString("author_name"),
            resultSet.getString("body"),
            CommentStatus.valueOf(resultSet.getString("status")),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private long totalItemsOrZero(Long totalItems) {
        return Objects.requireNonNullElse(totalItems, 0L);
    }

    private record CommentRow(
        UUID id,
        UUID siteId,
        UUID pageId,
        UUID parentId,
        UUID authorUserId,
        String authorEmail,
        String authorName,
        String body,
        CommentStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    private final class MutableComment {

        private final CommentRow row;
        private final List<MutableComment> replies = new ArrayList<>();

        private MutableComment(CommentRow row) {
            this.row = row;
        }

        private List<MutableComment> replies() {
            return replies;
        }

        private CommentView toView() {
            return toCommentView(row, replies.stream().map(MutableComment::toView).toList());
        }
    }
}
