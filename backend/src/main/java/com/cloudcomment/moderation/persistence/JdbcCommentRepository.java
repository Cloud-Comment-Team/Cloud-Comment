package com.cloudcomment.moderation.persistence;

import com.cloudcomment.moderation.application.ModerationCommentFilters;
import com.cloudcomment.moderation.application.ModerationCommentPage;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentAuthor;
import com.cloudcomment.moderation.domain.CommentSortField;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ParentComment;
import com.cloudcomment.moderation.domain.SortOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcCommentRepository implements CommentRepository {

    private static final String COMMENT_SELECT = """
        select
            c.id,
            p.site_id,
            c.page_id,
            p.url as page_url,
            c.parent_id,
            c.author_user_id,
            u.email as author_email,
            coalesce(u.display_name, c.author_name) as author_display_name,
            c.body,
            c.status,
            c.created_at,
            c.updated_at,
            pc.author_user_id as parent_author_user_id,
            coalesce(pu.email, pc.author_email) as parent_author_email,
            coalesce(pu.display_name, pc.author_name, pc.author_email, pu.email) as parent_author_display_name,
            pc.body as parent_body,
            pc.status as parent_status,
            pc.created_at as parent_created_at
        from comments c
        join pages p on p.id = c.page_id
        join sites s on s.id = p.site_id
        left join app_users u on u.id = c.author_user_id
        left join comments pc on pc.id = c.parent_id
        left join app_users pu on pu.id = pc.author_user_id
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public ModerationCommentPage findByOwnerId(
        UUID ownerId,
        ModerationCommentFilters filters,
        int page,
        int pageSize
    ) {
        FilterQuery filterQuery = buildFilterQuery(ownerId, filters);
        long offset = ((long) page - 1) * pageSize;

        List<Comment> items = jdbcTemplate.query(
            COMMENT_SELECT + filterQuery.whereClause() + orderByClause(filters) + " limit ? offset ?",
            this::mapCommentRow,
            appendPaginationParams(filterQuery.params(), pageSize, offset)
        );

        Long totalItems = jdbcTemplate.queryForObject(
            """
                select count(*)
                from comments c
                join pages p on p.id = c.page_id
                join sites s on s.id = p.site_id
                """ + filterQuery.whereClause(),
            Long.class,
            filterQuery.params().toArray()
        );

        return new ModerationCommentPage(
            items,
            page,
            pageSize,
            Objects.requireNonNullElse(totalItems, 0L)
        );
    }

    @Override
    public Optional<Comment> findById(UUID commentId) {
        List<Comment> comments = jdbcTemplate.query(
            COMMENT_SELECT + " where c.id = ?",
            this::mapCommentRow,
            commentId
        );
        return comments.stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<Comment> updateStatus(
        UUID commentId,
        CommentStatus expectedStatus,
        CommentStatus newStatus,
        String moderationReason
    ) {
        int updated = jdbcTemplate.update(
            """
                update comments
                set status = ?,
                    moderation_reason = ?,
                    moderated_at = now(),
                    updated_at = now()
                where id = ?
                  and status = ?
                """,
            newStatus.name(),
            moderationReason,
            commentId,
            expectedStatus.name()
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(commentId);
    }

    private FilterQuery buildFilterQuery(UUID ownerId, ModerationCommentFilters filters) {
        StringBuilder where = new StringBuilder(" where s.owner_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(ownerId);

        if (filters.siteId() != null) {
            where.append(" and p.site_id = ?");
            params.add(filters.siteId());
        }
        if (filters.pageId() != null) {
            where.append(" and c.page_id = ?");
            params.add(filters.pageId());
        }
        if (filters.pageUrl() != null) {
            where.append(" and p.url = ?");
            params.add(filters.pageUrl());
        }
        if (filters.status() != null) {
            where.append(" and c.status = ?");
            params.add(filters.status().name());
        }
        if (filters.createdFrom() != null) {
            where.append(" and c.created_at >= ?");
            params.add(toOffsetDateTime(filters.createdFrom()));
        }
        if (filters.createdTo() != null) {
            where.append(" and c.created_at <= ?");
            params.add(toOffsetDateTime(filters.createdTo()));
        }
        if (filters.search() != null && !filters.search().isBlank()) {
            where.append(" and c.body ilike ?");
            params.add("%" + filters.search().trim() + "%");
        }

        return new FilterQuery(where.toString(), params);
    }

    private String orderByClause(ModerationCommentFilters filters) {
        CommentSortField sortBy = filters.sortBy() != null ? filters.sortBy() : CommentSortField.CREATED_AT;
        SortOrder sortOrder = filters.sortOrder() != null ? filters.sortOrder() : SortOrder.DESC;
        String column = switch (sortBy) {
            case CREATED_AT -> "c.created_at";
            case UPDATED_AT -> "c.updated_at";
            case STATUS -> "c.status";
        };
        return " order by " + column + " " + sortOrder.name() + ", c.id " + sortOrder.name();
    }

    private Object[] appendPaginationParams(List<Object> params, int pageSize, long offset) {
        List<Object> paginationParams = new ArrayList<>(params);
        paginationParams.add(pageSize);
        paginationParams.add(offset);
        return paginationParams.toArray();
    }

    private Comment mapCommentRow(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID authorUserId = resultSet.getObject("author_user_id", UUID.class);
        return new Comment(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("site_id", UUID.class),
            resultSet.getObject("page_id", UUID.class),
            resultSet.getString("page_url"),
            resultSet.getObject("parent_id", UUID.class),
            mapParentComment(resultSet),
            new CommentAuthor(
                authorUserId,
                resultSet.getString("author_email"),
                resultSet.getString("author_display_name")
            ),
            resultSet.getString("body"),
            CommentStatus.valueOf(resultSet.getString("status")),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at")
        );
    }

    private ParentComment mapParentComment(ResultSet resultSet) throws SQLException {
        UUID parentId = resultSet.getObject("parent_id", UUID.class);
        if (parentId == null) {
            return null;
        }
        return new ParentComment(
            parentId,
            new CommentAuthor(
                resultSet.getObject("parent_author_user_id", UUID.class),
                resultSet.getString("parent_author_email"),
                resultSet.getString("parent_author_display_name")
            ),
            resultSet.getString("parent_body"),
            CommentStatus.valueOf(resultSet.getString("parent_status")),
            toInstant(resultSet, "parent_created_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private record FilterQuery(String whereClause, List<Object> params) {
    }
}
