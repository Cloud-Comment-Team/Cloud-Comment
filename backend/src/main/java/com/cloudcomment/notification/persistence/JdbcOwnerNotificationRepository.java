package com.cloudcomment.notification.persistence;

import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.notification.application.OwnerNotificationPage;
import com.cloudcomment.notification.domain.OwnerNotification;
import com.cloudcomment.notification.domain.OwnerNotificationView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcOwnerNotificationRepository implements OwnerNotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public OwnerNotification create(UUID ownerId, UUID commentId, String deduplicationKey, Instant createdAt) {
        return jdbcTemplate.queryForObject(
            """
                insert into owner_notifications (owner_id, comment_id, deduplication_key, created_at)
                values (?, ?, ?, ?)
                on conflict (owner_id, deduplication_key) do update
                    set deduplication_key = excluded.deduplication_key
                returning id, owner_id, comment_id, read_at, created_at
                """,
            this::mapNotification,
            ownerId,
            commentId,
            deduplicationKey,
            toOffsetDateTime(createdAt)
        );
    }

    @Override
    public OwnerNotificationPage findByOwnerId(UUID ownerId, int page, int pageSize) {
        long offset = (long) (page - 1) * pageSize;
        List<OwnerNotificationView> items = jdbcTemplate.query(
            viewSelect() + """
                where n.owner_id = ?
                order by n.created_at desc, n.id desc
                limit ? offset ?
                """,
            this::mapView,
            ownerId,
            pageSize,
            offset
        );
        Long total = jdbcTemplate.queryForObject(
            "select count(*) from owner_notifications where owner_id = ?",
            Long.class,
            ownerId
        );
        return new OwnerNotificationPage(items, page, pageSize, total == null ? 0 : total);
    }

    @Override
    public long countUnreadByOwnerId(UUID ownerId) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from owner_notifications where owner_id = ? and read_at is null",
            Long.class,
            ownerId
        );
        return count == null ? 0 : count;
    }

    @Override
    @Transactional
    public Optional<OwnerNotificationView> markRead(UUID ownerId, UUID notificationId, Instant readAt) {
        int updated = jdbcTemplate.update(
            """
                update owner_notifications
                set read_at = coalesce(read_at, ?)
                where id = ? and owner_id = ?
                """,
            toOffsetDateTime(readAt),
            notificationId,
            ownerId
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            viewSelect() + " where n.id = ? and n.owner_id = ?",
            this::mapView,
            notificationId,
            ownerId
        ).stream().findFirst();
    }

    @Override
    @Transactional
    public int markAllRead(UUID ownerId, Instant readAt) {
        return jdbcTemplate.update(
            "update owner_notifications set read_at = ? where owner_id = ? and read_at is null",
            toOffsetDateTime(readAt),
            ownerId
        );
    }

    @Override
    @Transactional
    public int deleteCreatedBefore(Instant threshold) {
        return jdbcTemplate.update(
            "delete from owner_notifications where created_at < ?",
            toOffsetDateTime(threshold)
        );
    }

    private String viewSelect() {
        return """
            select n.id, n.comment_id, p.site_id, s.name as site_name, c.page_id, p.url as page_url,
                   c.parent_id, coalesce(c.author_email, u.email) as author_email,
                   c.body, c.status, n.read_at, n.created_at
            from owner_notifications n
            join comments c on c.id = n.comment_id
            join pages p on p.id = c.page_id
            join sites s on s.id = p.site_id and s.owner_id = n.owner_id
            left join app_users u on u.id = c.author_user_id
            """;
    }

    private OwnerNotification mapNotification(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OwnerNotification(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("owner_id", UUID.class),
            resultSet.getObject("comment_id", UUID.class),
            toInstant(resultSet, "read_at"),
            toInstant(resultSet, "created_at")
        );
    }

    private OwnerNotificationView mapView(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OwnerNotificationView(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("comment_id", UUID.class),
            resultSet.getObject("site_id", UUID.class),
            resultSet.getString("site_name"),
            resultSet.getObject("page_id", UUID.class),
            resultSet.getString("page_url"),
            resultSet.getObject("parent_id", UUID.class),
            resultSet.getString("author_email"),
            resultSet.getString("body"),
            CommentStatus.valueOf(resultSet.getString("status")),
            toInstant(resultSet, "read_at"),
            toInstant(resultSet, "created_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
