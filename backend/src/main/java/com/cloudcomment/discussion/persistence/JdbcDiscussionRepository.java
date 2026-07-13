package com.cloudcomment.discussion.persistence;

import com.cloudcomment.discussion.application.DiscussionFilters;
import com.cloudcomment.discussion.application.DiscussionPage;
import com.cloudcomment.discussion.domain.DiscussionAuthor;
import com.cloudcomment.discussion.domain.DiscussionMessage;
import com.cloudcomment.discussion.domain.DiscussionStatus;
import com.cloudcomment.discussion.domain.DiscussionSummary;
import com.cloudcomment.discussion.domain.DiscussionThread;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcDiscussionRepository implements DiscussionRepository {

    private static final String ANONYMOUS_DISPLAY_NAME = "Участник";

    private static final String DISCUSSION_ROWS = """
        with discussion_rows as (
            select
                root.id as root_comment_id,
                s.id as site_id,
                s.name as site_name,
                p.id as page_id,
                p.url as page_url,
                p.title as page_title,
                latest.author_user_id as last_author_user_id,
                latest.author_email as last_author_email,
                latest.author_display_name as last_author_display_name,
                latest.body as last_message,
                latest.updated_at as last_activity_at,
                (
                    select count(*)
                    from comments reply_count
                    where reply_count.parent_id = root.id
                      and reply_count.status = 'APPROVED'
                      and reply_count.deleted_at is null
                ) as reply_count,
                exists (
                    select 1
                    from owner_notifications notification
                    join comments notified_comment on notified_comment.id = notification.comment_id
                    where notification.owner_id = s.owner_id
                      and notification.read_at is null
                      and (notified_comment.id = root.id or notified_comment.parent_id = root.id)
                ) as unread,
                (latest.author_user_id is distinct from s.owner_id) as needs_reply,
                root.is_pinned
            from comments root
            join pages p on p.id = root.page_id
            join sites s on s.id = p.site_id
            join lateral (
                select
                    message.author_user_id,
                    coalesce(author.email, message.author_email) as author_email,
                    coalesce(author.display_name, message.author_name) as author_display_name,
                    message.body,
                    message.created_at,
                    message.updated_at
                from comments message
                left join app_users author on author.id = message.author_user_id
                where (message.id = root.id or message.parent_id = root.id)
                  and message.status = 'APPROVED'
                  and message.deleted_at is null
                order by message.created_at desc, message.id desc
                limit 1
            ) latest on true
            where s.owner_id = ?
              and root.parent_id is null
              and root.status = 'APPROVED'
              and root.deleted_at is null
        )
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public DiscussionPage findByOwnerId(UUID ownerId, DiscussionFilters filters, int page, int pageSize) {
        OuterFilter outerFilter = outerFilter(filters);
        long offset = ((long) page - 1) * pageSize;
        List<Object> itemParams = new ArrayList<>();
        itemParams.add(ownerId);
        itemParams.addAll(outerFilter.params());
        itemParams.add(pageSize);
        itemParams.add(offset);

        List<DiscussionSummary> items = jdbcTemplate.query(
            DISCUSSION_ROWS
                + " select * from discussion_rows "
                + outerFilter.whereClause()
                + " order by unread desc, needs_reply desc, last_activity_at desc, root_comment_id desc limit ? offset ?",
            this::mapSummary,
            itemParams.toArray()
        );

        List<Object> countParams = new ArrayList<>();
        countParams.add(ownerId);
        countParams.addAll(outerFilter.params());
        Long totalItems = jdbcTemplate.queryForObject(
            DISCUSSION_ROWS + " select count(*) from discussion_rows " + outerFilter.whereClause(),
            Long.class,
            countParams.toArray()
        );
        return new DiscussionPage(items, page, pageSize, Objects.requireNonNullElse(totalItems, 0L));
    }

    @Override
    public Optional<DiscussionThread> findThreadByOwnerId(UUID ownerId, UUID rootCommentId) {
        List<DiscussionSummary> summaries = jdbcTemplate.query(
            DISCUSSION_ROWS + " select * from discussion_rows where root_comment_id = ?",
            this::mapSummary,
            ownerId,
            rootCommentId
        );
        if (summaries.isEmpty()) {
            return Optional.empty();
        }

        List<DiscussionMessage> messages = jdbcTemplate.query(
            """
                select
                    message.id,
                    message.parent_id,
                    message.author_user_id,
                    coalesce(author.email, message.author_email) as author_email,
                    coalesce(author.display_name, message.author_name) as author_display_name,
                    message.body,
                    message.created_at,
                    message.updated_at,
                    message.is_pinned,
                    s.owner_id
                from comments message
                join pages p on p.id = message.page_id
                join sites s on s.id = p.site_id
                left join app_users author on author.id = message.author_user_id
                where s.owner_id = ?
                  and (message.id = ? or message.parent_id = ?)
                  and message.status = 'APPROVED'
                  and message.deleted_at is null
                order by message.created_at asc, message.id asc
                """,
            this::mapMessage,
            ownerId,
            rootCommentId,
            rootCommentId
        );
        return Optional.of(new DiscussionThread(summaries.getFirst(), messages));
    }

    private OuterFilter outerFilter(DiscussionFilters filters) {
        List<String> predicates = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (filters.siteId() != null) {
            predicates.add("site_id = ?");
            params.add(filters.siteId());
        }
        switch (filters.view()) {
            case UNREAD -> predicates.add("unread");
            case NEEDS_REPLY -> predicates.add("needs_reply");
            case ALL -> {
            }
        }
        if (filters.search() != null) {
            predicates.add("""
                (site_name ilike ?
                    or coalesce(page_title, '') ilike ?
                    or page_url ilike ?
                    or exists (
                        select 1
                        from comments search_message
                        where (search_message.id = root_comment_id or search_message.parent_id = root_comment_id)
                          and search_message.status = 'APPROVED'
                          and search_message.deleted_at is null
                          and search_message.body ilike ?
                    ))
                """);
            String pattern = "%" + filters.search() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        String whereClause = predicates.isEmpty() ? "" : " where " + String.join(" and ", predicates);
        return new OuterFilter(whereClause, params);
    }

    private DiscussionSummary mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        boolean needsReply = resultSet.getBoolean("needs_reply");
        UUID authorId = resultSet.getObject("last_author_user_id", UUID.class);
        return new DiscussionSummary(
            resultSet.getObject("root_comment_id", UUID.class),
            resultSet.getObject("site_id", UUID.class),
            resultSet.getString("site_name"),
            resultSet.getObject("page_id", UUID.class),
            resultSet.getString("page_url"),
            resultSet.getString("page_title"),
            new DiscussionAuthor(
                authorId,
                safeDisplayName(
                    resultSet.getString("last_author_display_name"),
                    resultSet.getString("last_author_email")
                ),
                !needsReply
            ),
            resultSet.getString("last_message"),
            toInstant(resultSet, "last_activity_at"),
            resultSet.getLong("reply_count"),
            resultSet.getBoolean("unread"),
            needsReply ? DiscussionStatus.NEEDS_REPLY : DiscussionStatus.ACTIVE,
            resultSet.getBoolean("is_pinned")
        );
    }

    private DiscussionMessage mapMessage(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID authorId = resultSet.getObject("author_user_id", UUID.class);
        UUID ownerId = resultSet.getObject("owner_id", UUID.class);
        return new DiscussionMessage(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("parent_id", UUID.class),
            new DiscussionAuthor(
                authorId,
                safeDisplayName(
                    resultSet.getString("author_display_name"),
                    resultSet.getString("author_email")
                ),
                ownerId.equals(authorId)
            ),
            resultSet.getString("body"),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at"),
            resultSet.getBoolean("is_pinned")
        );
    }

    private String safeDisplayName(String displayName, String email) {
        if (displayName == null) {
            return ANONYMOUS_DISPLAY_NAME;
        }
        String normalized = displayName.trim();
        if (normalized.isEmpty()
            || normalized.contains("@")
            || (email != null && normalized.equalsIgnoreCase(email.trim()))) {
            return ANONYMOUS_DISPLAY_NAME;
        }
        return normalized;
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private record OuterFilter(String whereClause, List<Object> params) {
    }
}
