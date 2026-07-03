package com.cloudcomment.account.persistence;

import com.cloudcomment.account.domain.AccountDeletionRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
class JdbcAccountDeletionRequestRepository implements AccountDeletionRequestRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcAccountDeletionRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AccountDeletionRequest> findActiveByUserId(UUID userId, Instant now) {
        List<AccountDeletionRequest> rows = jdbcTemplate.query(
            """
                select id, user_id, token_hash, created_at, expires_at, confirmed_at, cancelled_at
                from account_deletion_requests
                where user_id = ?
                  and confirmed_at is null
                  and cancelled_at is null
                  and expires_at > ?
                order by created_at desc
                limit 1
                """,
            this::mapRow,
            userId,
            OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
        );
        return rows.stream().findFirst();
    }

    @Override
    public Optional<AccountDeletionRequest> findByTokenHash(String tokenHash) {
        List<AccountDeletionRequest> rows = jdbcTemplate.query(
            """
                select id, user_id, token_hash, created_at, expires_at, confirmed_at, cancelled_at
                from account_deletion_requests
                where token_hash = ?
                """,
            this::mapRow,
            tokenHash
        );
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public AccountDeletionRequest create(UUID userId, String tokenHash, Instant expiresAt) {
        AccountDeletionRequest created = jdbcTemplate.queryForObject(
            """
                insert into account_deletion_requests (user_id, token_hash, expires_at)
                values (?, ?, ?)
                returning id, user_id, token_hash, created_at, expires_at, confirmed_at, cancelled_at
                """,
            this::mapRow,
            userId,
            tokenHash,
            OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        );
        return Objects.requireNonNull(created, "created deletion request must not be null");
    }

    @Override
    @Transactional
    public AccountDeletionRequest rotateToken(UUID requestId, String tokenHash, Instant expiresAt, Instant now) {
        List<AccountDeletionRequest> rows = jdbcTemplate.query(
            """
                update account_deletion_requests
                set token_hash = ?,
                    expires_at = ?,
                    created_at = ?
                where id = ?
                  and confirmed_at is null
                  and cancelled_at is null
                returning id, user_id, token_hash, created_at, expires_at, confirmed_at, cancelled_at
                """,
            this::mapRow,
            tokenHash,
            OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
            requestId
        );
        return rows.stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Active deletion request not found: " + requestId));
    }

    @Override
    @Transactional
    public void markConfirmed(UUID requestId, Instant confirmedAt) {
        if (!tryMarkConfirmed(requestId, confirmedAt)) {
            throw new IllegalStateException("Deletion request is not pending: " + requestId);
        }
    }

    @Override
    @Transactional
    public boolean tryMarkConfirmed(UUID requestId, Instant confirmedAt) {
        return jdbcTemplate.update(
            """
                update account_deletion_requests
                set confirmed_at = ?
                where id = ?
                  and confirmed_at is null
                  and cancelled_at is null
                """,
            OffsetDateTime.ofInstant(confirmedAt, ZoneOffset.UTC),
            requestId
        ) > 0;
    }

    @Override
    @Transactional
    public void cancelPendingForUser(UUID userId, Instant cancelledAt) {
        jdbcTemplate.update(
            """
                update account_deletion_requests
                set cancelled_at = ?
                where user_id = ?
                  and confirmed_at is null
                  and cancelled_at is null
                """,
            OffsetDateTime.ofInstant(cancelledAt, ZoneOffset.UTC),
            userId
        );
    }

    private AccountDeletionRequest mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AccountDeletionRequest(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("user_id", UUID.class),
            resultSet.getString("token_hash"),
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
}
