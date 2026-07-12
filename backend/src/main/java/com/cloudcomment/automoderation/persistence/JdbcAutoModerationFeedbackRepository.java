package com.cloudcomment.automoderation.persistence;

import com.cloudcomment.automoderation.domain.AutoModerationFeedback;
import com.cloudcomment.automoderation.domain.AutoModerationFeedbackType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
class JdbcAutoModerationFeedbackRepository implements AutoModerationFeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<AutoModerationFeedback> upsertCurrent(
        UUID ownerId,
        UUID commentId,
        AutoModerationFeedbackType type,
        Instant createdAt
    ) {
        List<AutoModerationFeedback> rows = jdbcTemplate.query(
            """
                insert into automod_policy_feedback (
                    comment_id, policy_version_id, owner_id, feedback_type, created_at
                )
                select c.id, c.automod_policy_version_id, s.owner_id, ?, ?
                from comments c
                join pages p on p.id = c.page_id
                join sites s on s.id = p.site_id
                where c.id = ?
                  and s.owner_id = ?
                  and c.automod_policy_version_id is not null
                  and (
                      (? = 'FALSE_POSITIVE' and c.automod_decision in ('REVIEW', 'SPAM'))
                      or
                      (? = 'FALSE_NEGATIVE' and c.automod_decision = 'APPROVE')
                  )
                on conflict (comment_id, policy_version_id)
                do update set feedback_type = excluded.feedback_type,
                              owner_id = excluded.owner_id,
                              created_at = excluded.created_at
                returning id, comment_id, policy_version_id, owner_id, feedback_type, created_at
                """,
            this::mapFeedback,
            type.name(),
            createdAt.atOffset(ZoneOffset.UTC),
            commentId,
            ownerId,
            type.name(),
            type.name()
        );
        return rows.stream().findFirst();
    }

    @Override
    public boolean deleteCurrent(UUID ownerId, UUID commentId) {
        return jdbcTemplate.update(
            """
                delete from automod_policy_feedback feedback
                using comments c, pages p, sites s
                where feedback.comment_id = c.id
                  and feedback.policy_version_id = c.automod_policy_version_id
                  and c.page_id = p.id
                  and p.site_id = s.id
                  and c.id = ?
                  and s.owner_id = ?
                """,
            commentId,
            ownerId
        ) > 0;
    }

    @Override
    public int deleteCreatedBefore(Instant threshold) {
        return jdbcTemplate.update(
            "delete from automod_policy_feedback where created_at < ?",
            threshold.atOffset(ZoneOffset.UTC)
        );
    }

    private AutoModerationFeedback mapFeedback(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AutoModerationFeedback(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("comment_id", UUID.class),
            resultSet.getObject("policy_version_id", UUID.class),
            resultSet.getObject("owner_id", UUID.class),
            AutoModerationFeedbackType.valueOf(resultSet.getString("feedback_type")),
            resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
