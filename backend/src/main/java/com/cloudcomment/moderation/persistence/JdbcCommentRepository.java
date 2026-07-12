package com.cloudcomment.moderation.persistence;

import com.cloudcomment.moderation.application.ModerationCommentFilters;
import com.cloudcomment.moderation.application.ModerationCommentPage;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentAuthor;
import com.cloudcomment.moderation.domain.CommentSortField;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationPriority;
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
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcCommentRepository implements CommentRepository {

    private static final Pattern LINK_OR_CONTACT_PATTERN = Pattern.compile(
        "(https?://|www\\.|telegram|whatsapp|@[a-z0-9_\\.-]+)",
        Pattern.CASE_INSENSITIVE
    );

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
            c.is_pinned,
            c.is_favorite,
            c.created_at,
            c.updated_at,
            pc.author_user_id as parent_author_user_id,
            coalesce(pu.email, pc.author_email) as parent_author_email,
            coalesce(pu.display_name, pc.author_name, pc.author_email, pu.email) as parent_author_display_name,
            pc.body as parent_body,
            pc.status as parent_status,
            pc.created_at as parent_created_at,
            c.moderation_reason,
            (
                case c.status
                    when 'PENDING' then 500
                    when 'SPAM' then 450
                    when 'REJECTED' then 120
                    when 'HIDDEN' then 80
                    else 40
                end
                + case when c.moderation_reason is not null and btrim(c.moderation_reason) <> '' then 120 else 0 end
                + case when c.body ~* '(https?://|www\\.|telegram|whatsapp|@[a-z0-9_\\.-]+)' then 80 else 0 end
                + case when length(c.body) >= 800 then 50 else 0 end
                + case when c.parent_id is not null then 35 else 0 end
                + least(120, greatest(0, floor(extract(epoch from (now() - c.created_at)) / 3600)::int * 5))
            ) as smart_priority_score
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
            COMMENT_SELECT + " where c.id = ? and c.deleted_at is null",
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

    @Override
    @Transactional
    public Optional<Comment> updateFlags(UUID commentId, Boolean pinned, Boolean favorite) {
        int updated = jdbcTemplate.update(
            """
                update comments
                set is_pinned = coalesce(?, is_pinned),
                    is_favorite = coalesce(?, is_favorite),
                    updated_at = now()
                where id = ?
                  and deleted_at is null
                """,
            pinned,
            favorite,
            commentId
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(commentId);
    }

    private FilterQuery buildFilterQuery(UUID ownerId, ModerationCommentFilters filters) {
        StringBuilder where = new StringBuilder(" where s.owner_id = ? and c.deleted_at is null");
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
        if (filters.favorite() != null) {
            where.append(" and c.is_favorite = ?");
            params.add(filters.favorite());
        }

        return new FilterQuery(where.toString(), params);
    }

    private String orderByClause(ModerationCommentFilters filters) {
        CommentSortField sortBy = filters.sortBy() != null ? filters.sortBy() : CommentSortField.SMART;
        SortOrder sortOrder = filters.sortOrder() != null ? filters.sortOrder() : SortOrder.DESC;
        String column = switch (sortBy) {
            case SMART -> "smart_priority_score";
            case CREATED_AT -> "c.created_at";
            case UPDATED_AT -> "c.updated_at";
            case STATUS -> "c.status";
            case PINNED -> "c.is_pinned";
            case FAVORITE -> "c.is_favorite";
        };
        if (sortBy == CommentSortField.SMART) {
            return " order by " + column + " " + sortOrder.name()
                + ", c.created_at asc, c.id " + sortOrder.name();
        }
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
            resultSet.getString("moderation_reason"),
            resultSet.getBoolean("is_pinned"),
            resultSet.getBoolean("is_favorite"),
            ModerationPriority.fromScore(resultSet.getInt("smart_priority_score")),
            resultSet.getInt("smart_priority_score"),
            priorityReasons(resultSet),
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

    private List<String> priorityReasons(ResultSet resultSet) throws SQLException {
        List<String> reasons = new ArrayList<>();
        CommentStatus status = CommentStatus.valueOf(resultSet.getString("status"));
        String moderationReason = resultSet.getString("moderation_reason");
        String body = resultSet.getString("body");
        boolean hasModerationReason = moderationReason != null && !moderationReason.isBlank();
        boolean hasLinkOrContact = body != null && LINK_OR_CONTACT_PATTERN.matcher(body).find();

        if (status == CommentStatus.PENDING) {
            reasons.add("Ожидает решения модератора");
        } else if (status == CommentStatus.SPAM) {
            reasons.add("Автомодерация пометила как спам");
        }
        if (hasModerationReason) {
            reasons.add("Есть объяснение автомодерации");
        }
        if (hasLinkOrContact) {
            reasons.add("Содержит ссылку или контакт");
        }
        if (body != null && body.length() >= 800) {
            reasons.add("Длинный комментарий требует внимательной проверки");
        }
        if (resultSet.getObject("parent_id", UUID.class) != null) {
            reasons.add("Ответ внутри обсуждения");
        }
        if (resultSet.getInt("smart_priority_score") >= 760) {
            reasons.add("Высокий суммарный риск");
        }
        return reasons;
    }

    private record FilterQuery(String whereClause, List<Object> params) {
    }
}
