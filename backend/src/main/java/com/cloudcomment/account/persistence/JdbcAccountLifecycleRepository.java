package com.cloudcomment.account.persistence;

import com.cloudcomment.account.application.RelatedPersonalDataAnonymization;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcAccountLifecycleRepository implements AccountLifecycleRepository {

    private static final String DELETED_AUTHOR_NAME = "Deleted user";
    private static final String DELETED_COMMENT_BODY = "Comment deleted by user";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public RelatedPersonalDataAnonymization anonymizeRelatedPersonalData(UUID userId, Instant anonymizedAt) {
        int ownedSitesDeleted = countOwnedSites(userId);
        jdbcTemplate.update("delete from sites where owner_id = ?", userId);

        int authoredCommentsAnonymized = jdbcTemplate.update(
            """
                update comments
                set author_user_id = null,
                    author_email = null,
                    author_name = ?,
                    body = ?,
                    updated_at = ?
                where author_user_id = ?
                """,
            DELETED_AUTHOR_NAME,
            DELETED_COMMENT_BODY,
            toOffsetDateTime(anonymizedAt),
            userId
        );

        int moderationActionsAnonymized = jdbcTemplate.update(
            """
                update moderation_actions
                set moderator_id = null
                where moderator_id = ?
                """,
            userId
        );

        int commentReactionsDeleted = jdbcTemplate.update(
            "delete from comment_reactions where user_id = ?",
            userId
        );

        return new RelatedPersonalDataAnonymization(
            ownedSitesDeleted,
            authoredCommentsAnonymized,
            moderationActionsAnonymized,
            commentReactionsDeleted
        );
    }

    @Override
    @Transactional
    public int deleteInactiveSessionsBefore(Instant cutoff) {
        OffsetDateTime cutoffOffset = toOffsetDateTime(cutoff);
        return jdbcTemplate.update(
            """
                delete from auth_sessions
                where (revoked_at is not null and revoked_at < ?)
                   or (revoked_at is null and expires_at < ?)
                """,
            cutoffOffset,
            cutoffOffset
        );
    }

    private int countOwnedSites(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from sites where owner_id = ?",
            Integer.class,
            userId
        );
        return count == null ? 0 : count;
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
