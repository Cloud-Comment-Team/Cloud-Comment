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
import java.util.List;
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
        return create(commentId, moderatorId, action, fromStatus, toStatus, reason, null, null);
    }

    @Override
    @Transactional
    public ModerationAction create(
        UUID commentId, UUID moderatorId, ModerationActionType action, CommentStatus fromStatus,
        CommentStatus toStatus, String reason, UUID operationId, UUID revertsActionId
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
                        reason,
                        operation_id,
                        reverts_action_id
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    returning id, comment_id, action, from_status, to_status, reason, moderator_id,
                              operation_id, reverts_action_id, created_at
                    """,
                this::mapActionRow,
                commentId,
                moderatorId,
                action.name(),
                fromStatus.name(),
                toStatus.name(),
                reason,
                operationId,
                revertsActionId
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

    @Override
    public java.util.Optional<ModerationAction> findById(UUID actionId) {
        return findOne("where ma.id = ?", actionId);
    }

    @Override
    public java.util.Optional<ModerationAction> findByCommentIdAndOperationId(UUID commentId, UUID operationId) {
        return findOne("where ma.comment_id = ? and ma.operation_id = ?", commentId, operationId);
    }

    @Override
    public java.util.Optional<ModerationAction> findLatestNotReverted(UUID commentId) {
        return findOne("""
            where ma.comment_id = ? and ma.action <> 'UNDO'
              and not exists (select 1 from moderation_actions undo where undo.reverts_action_id = ma.id)
            order by ma.created_at desc, ma.id desc limit 1
            """, commentId);
    }

    @Override
    public List<ModerationAction> findByOperationIdAndOwnerId(UUID operationId, UUID ownerId) {
        return jdbcTemplate.query("""
            select ma.id, ma.comment_id, ma.action, ma.from_status, ma.to_status, ma.reason,
                   ma.moderator_id, ma.operation_id, ma.reverts_action_id, ma.created_at, u.email
            from moderation_actions ma
            join comments c on c.id = ma.comment_id
            join pages p on p.id = c.page_id
            join sites s on s.id = p.site_id
            left join app_users u on u.id = ma.moderator_id
            where ma.operation_id = ?
              and s.owner_id = ?
              and ma.action <> 'UNDO'
            order by ma.created_at asc, ma.id asc
            """, (resultSet, rowNumber) -> toModerationAction(
                mapActionRow(resultSet, rowNumber),
                resultSet.getString("email")
            ), operationId, ownerId);
    }

    private java.util.Optional<ModerationAction> findOne(String clause, Object... params) {
        return jdbcTemplate.query("""
            select ma.id, ma.comment_id, ma.action, ma.from_status, ma.to_status, ma.reason,
                   ma.moderator_id, ma.operation_id, ma.reverts_action_id, ma.created_at, u.email
            from moderation_actions ma left join app_users u on u.id = ma.moderator_id
            """ + clause, (resultSet, rowNumber) -> toModerationAction(mapActionRow(resultSet, rowNumber), resultSet.getString("email")), params)
            .stream().findFirst();
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
            row.operationId(),
            row.revertsActionId(),
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
            resultSet.getObject("operation_id", UUID.class),
            resultSet.getObject("reverts_action_id", UUID.class),
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
        UUID operationId,
        UUID revertsActionId,
        Instant createdAt
    ) {
    }
}
