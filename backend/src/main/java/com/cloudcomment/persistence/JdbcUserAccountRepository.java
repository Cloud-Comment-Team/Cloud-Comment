package com.cloudcomment.persistence;

import com.cloudcomment.service.RegisteredUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
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

    private RegisteredUser mapRegisteredUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RegisteredUser(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("email"),
            Set.of(),
            toInstant(resultSet, "created_at"),
            toInstant(resultSet, "updated_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
