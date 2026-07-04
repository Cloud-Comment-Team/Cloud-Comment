package com.cloudcomment.account.persistence;

import com.cloudcomment.account.application.PersonalDataSnapshot;
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
class JdbcPersonalDataRepository implements PersonalDataRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<PersonalDataSnapshot> findByUserId(UUID userId, Instant now) {
        return findAccount(userId).map(account -> new PersonalDataSnapshot(
            account,
            findRoles(userId),
            findConsents(userId),
            findSessions(userId, now),
            findResources(userId),
            findDeletionRequest(userId).orElse(null),
            now
        ));
    }

    private Optional<PersonalDataSnapshot.AccountProfile> findAccount(UUID userId) {
        List<PersonalDataSnapshot.AccountProfile> rows = jdbcTemplate.query(
            """
                select id, email, display_name, is_enabled, created_at, updated_at, deleted_at
                from app_users
                where id = ?
                """,
            this::mapAccount,
            userId
        );
        return rows.stream().findFirst();
    }

    private List<String> findRoles(UUID userId) {
        return jdbcTemplate.queryForList(
            """
                select role
                from user_roles
                where user_id = ?
                order by role
                """,
            String.class,
            userId
        );
    }

    private List<PersonalDataSnapshot.Consent> findConsents(UUID userId) {
        return jdbcTemplate.query(
            """
                select privacy_policy_version, terms_version, source, accepted_at
                from user_consents
                where user_id = ?
                order by accepted_at desc, id desc
                """,
            this::mapConsent,
            userId
        );
    }

    private PersonalDataSnapshot.Sessions findSessions(UUID userId, Instant now) {
        OffsetDateTime nowOffset = toOffsetDateTime(now);
        return jdbcTemplate.queryForObject(
            """
                select
                    count(*) filter (where revoked_at is null and expires_at > ?) as active,
                    count(*) filter (where revoked_at is not null) as revoked,
                    count(*) filter (where revoked_at is null and expires_at <= ?) as expired
                from auth_sessions
                where user_id = ?
                """,
            (resultSet, rowNumber) -> new PersonalDataSnapshot.Sessions(
                resultSet.getInt("active"),
                resultSet.getInt("revoked"),
                resultSet.getInt("expired")
            ),
            nowOffset,
            nowOffset,
            userId
        );
    }

    private PersonalDataSnapshot.Resources findResources(UUID userId) {
        return new PersonalDataSnapshot.Resources(
            count("select count(*) from sites where owner_id = ?", userId),
            count(
                """
                    select count(*)
                    from pages p
                    join sites s on s.id = p.site_id
                    where s.owner_id = ?
                    """,
                userId
            ),
            count(
                """
                    select count(*)
                    from comments c
                    join pages p on p.id = c.page_id
                    join sites s on s.id = p.site_id
                    where s.owner_id = ?
                    """,
                userId
            ),
            count("select count(*) from comments where author_user_id = ?", userId),
            count("select count(*) from moderation_actions where moderator_id = ?", userId),
            count("select count(*) from comment_reactions where user_id = ?", userId)
        );
    }

    private Optional<PersonalDataSnapshot.DeletionRequest> findDeletionRequest(UUID userId) {
        List<PersonalDataSnapshot.DeletionRequest> rows = jdbcTemplate.query(
            """
                select id, created_at, expires_at, confirmed_at, cancelled_at,
                       case
                           when confirmed_at is not null then 'CONFIRMED'
                           when cancelled_at is not null then 'CANCELLED'
                           when expires_at <= now() then 'EXPIRED'
                           else 'PENDING'
                       end as status
                from account_deletion_requests
                where user_id = ?
                order by created_at desc
                limit 1
                """,
            this::mapDeletionRequest,
            userId
        );
        return rows.stream().findFirst();
    }

    private int count(String sql, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count == null ? 0 : count;
    }

    private PersonalDataSnapshot.AccountProfile mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PersonalDataSnapshot.AccountProfile(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("email"),
            resultSet.getString("display_name"),
            resultSet.getBoolean("is_enabled"),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at"),
            toOptionalInstant(resultSet, "deleted_at")
        );
    }

    private PersonalDataSnapshot.Consent mapConsent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PersonalDataSnapshot.Consent(
            resultSet.getString("privacy_policy_version"),
            resultSet.getString("terms_version"),
            resultSet.getString("source"),
            toInstant(resultSet, "accepted_at")
        );
    }

    private PersonalDataSnapshot.DeletionRequest mapDeletionRequest(ResultSet resultSet, int rowNumber)
        throws SQLException {
        return new PersonalDataSnapshot.DeletionRequest(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("status"),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "expires_at"),
            toOptionalInstant(resultSet, "confirmed_at"),
            toOptionalInstant(resultSet, "cancelled_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private Instant toOptionalInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
