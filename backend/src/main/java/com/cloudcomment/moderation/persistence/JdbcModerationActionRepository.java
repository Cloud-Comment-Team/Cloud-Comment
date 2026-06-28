package com.cloudcomment.moderation.persistence;

import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.ModerationActionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcModerationActionRepository implements ModerationActionRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public ModerationAction create(
        UUID commentId,
        UUID moderatorId,
        ModerationActionType action,
        CommentStatus fromStatus,
        CommentStatus toStatus,
        String reason
    ) {
        ModerationActionRow row = Objects.requireNonNull(
            jdbcTemplate.queryForObject(
                """
                    insert into moderation_actions (
                        comment_id,
                        moderator_id,
                        action,
                        from_status,
                        to_status,
                        reason
                    )
                    values (?, ?, ?, ?, ?, ?)
                    returning id, comment_id, action, from_status, to_status, reason, moderator_id, created_at
                    """,
                this::mapActionRow,
                commentId,
                moderatorId,
                action.name(),
                fromStatus.name(),
                toStatus.name(),
                reason
            ),
            "created moderation action must not be null"
        );

        String moderatorEmail = jdbcTemplate.queryForObject(
            "select email from app_users where id = ?",
            String.class,
            moderatorId
        );

        return toModerationAction(row, moderatorEmail);
    }

    private ModerationAction toModerationAction(ModerationActionRow row, String moderatorEmail) {
        return new ModerationAction(
            row.id(),
            row.commentId(),
            ModerationActionType.valueOf(row.action()),
            CommentStatus.valueOf(row.fromStatus()),
            CommentStatus.valueOf(row.toStatus()),
            row.reason(),
            row.moderatorId(),
            moderatorEmail,
            row.createdAt()
        );
    }

    private ModerationActionRow mapActionRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ModerationActionRow(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("comment_id", UUID.class),
            resultSet.getString("action"),
            resultSet.getString("from_status"),
            resultSet.getString("to_status"),
            resultSet.getString("reason"),
            resultSet.getObject("moderator_id", UUID.class),
            toInstant(resultSet, "created_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private record ModerationActionRow(
        UUID id,
        UUID commentId,
        String action,
        String fromStatus,
        String toStatus,
        String reason,
        UUID moderatorId,
        Instant createdAt
    ) {
    }
}
