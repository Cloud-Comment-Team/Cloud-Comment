package com.cloudcomment.persistence;

import com.cloudcomment.service.RegisteredUser;
import com.cloudcomment.service.UserCredentials;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class JdbcUserAccountRepository implements UserAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByEmail(String email) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select exists(select 1 from app_users where email = ?)",
            Boolean.class,
            email
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        List<UserCredentialsRow> rows = jdbcTemplate.query(
            """
                select id, email, password_hash, is_enabled, created_at, updated_at
                from app_users
                where email = ?
                """,
            this::mapUserCredentialsRow,
            email
        );

        return rows.stream()
            .findFirst()
            .map(row -> new UserCredentials(
                row.id(),
                row.email(),
                row.passwordHash(),
                row.enabled(),
                findRoles(row.id()),
                row.createdAt(),
                row.updatedAt()
            ));
    }

    @Override
    public RegisteredUser create(String email, String passwordHash, Set<String> roles) {
        RegisteredUser user = jdbcTemplate.queryForObject(
            """
                insert into app_users (email, password_hash)
                values (?, ?)
                returning id, email, created_at, updated_at
                """,
            this::mapRegisteredUser,
            email,
            passwordHash
        );

        RegisteredUser createdUser = Objects.requireNonNull(user, "created user must not be null");
        for (String role : roles) {
            jdbcTemplate.update(
                "insert into user_roles (user_id, role) values (?, ?)",
                createdUser.id(),
                role
            );
        }

        return new RegisteredUser(
            createdUser.id(),
            createdUser.email(),
            roles,
            createdUser.createdAt(),
            createdUser.updatedAt()
        );
    }

    @Override
    public void createSession(UUID userId, String tokenHash, Instant expiresAt) {
        jdbcTemplate.update(
            """
                insert into auth_sessions (user_id, token_hash, expires_at)
                values (?, ?, ?)
                """,
            userId,
            tokenHash,
            OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        );
    }

    private RegisteredUser mapRegisteredUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RegisteredUser(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("email"),
            Set.of(),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at")
        );
    }

    private UserCredentialsRow mapUserCredentialsRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserCredentialsRow(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("email"),
            resultSet.getString("password_hash"),
            resultSet.getBoolean("is_enabled"),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at")
        );
    }

    private Set<String> findRoles(UUID userId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
            """
                select role
                from user_roles
                where user_id = ?
                order by role
                """,
            String.class,
            userId
        ));
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private record UserCredentialsRow(
        UUID id,
        String email,
        String passwordHash,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
