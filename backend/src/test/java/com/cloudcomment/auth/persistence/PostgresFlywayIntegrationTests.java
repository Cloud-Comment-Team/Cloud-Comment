package com.cloudcomment.auth.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresFlywayIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Test
    void applicationConnectsToPostgresAndFlywayCreatesSchemaHistory() {
        assertThat(dataSource).isNotNull();
        assertThat(flyway).isNotNull();

        String databaseVersion = jdbcTemplate.queryForObject("select version()", String.class);
        Integer schemaHistoryRows = jdbcTemplate.queryForObject("select count(*) from flyway_schema_history", Integer.class);
        Integer smokeTableRows = jdbcTemplate.queryForObject("select count(*) from flyway_smoke_test", Integer.class);
        Integer coreTableRows = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name in (
                'app_users',
                'roles',
                'user_roles',
                'auth_sessions',
                'sites',
                'site_allowed_origins',
                'pages',
                'comments',
                'moderation_actions',
                'account_deletion_requests',
                'user_consents',
                'privacy_events'
            )
            """, Integer.class);
        Integer roleRows = jdbcTemplate.queryForObject("""
            select count(*)
            from roles
            where name in ('OWNER', 'COMMENTER', 'MODERATOR')
            """, Integer.class);

        assertThat(databaseVersion).contains("PostgreSQL");
        assertThat(schemaHistoryRows).isEqualTo(9);
        assertThat(smokeTableRows).isZero();
        assertThat(coreTableRows).isEqualTo(12);
        assertThat(roleRows).isEqualTo(3);
    }

    @Test
    void repositoryCreatesUsersReadsCredentialsAndStoresSessions() {
        String email = "repo-" + UUID.randomUUID() + "@example.com";
        Instant referenceTime = Instant.now();
        Instant activeAt = referenceTime.plus(Duration.ofDays(1));
        Instant alreadyRevokedAt = activeAt.plus(Duration.ofMinutes(5));
        Instant skewedPastAt = referenceTime.minus(Duration.ofDays(1));
        Instant expiresAt = referenceTime.plus(Duration.ofDays(7));
        Instant expiredAt = expiresAt.plus(Duration.ofDays(1));

        assertThat(userAccountRepository.existsByEmail(email)).isFalse();
        assertThat(userAccountRepository.findCredentialsByEmail(email)).isEmpty();

        var user = userAccountRepository.create(email, "hashed-password", Set.of("COMMENTER"));

        assertThat(userAccountRepository.existsByEmail(email)).isTrue();
        assertThat(user.id()).isNotNull();
        assertThat(user.email()).isEqualTo(email);
        assertThat(user.roles()).containsExactly("COMMENTER");
        assertThat(user.createdAt()).isNotNull();
        assertThat(user.updatedAt()).isNotNull();

        var credentials = userAccountRepository.findCredentialsByEmail(email).orElseThrow();
        assertThat(credentials.id()).isEqualTo(user.id());
        assertThat(credentials.email()).isEqualTo(email);
        assertThat(credentials.passwordHash()).isEqualTo("hashed-password");
        assertThat(credentials.enabled()).isTrue();
        assertThat(credentials.roles()).containsExactly("COMMENTER");
        assertThat(credentials.createdAt()).isEqualTo(user.createdAt());
        assertThat(credentials.updatedAt()).isEqualTo(user.updatedAt());

        userAccountRepository.createSession(user.id(), "a".repeat(64), expiresAt);

        Integer sessions = jdbcTemplate.queryForObject(
            "select count(*) from auth_sessions where user_id = ? and token_hash = ?",
            Integer.class,
            user.id(),
            "a".repeat(64)
        );
        assertThat(sessions).isOne();

        var currentUser = userAccountRepository.findUserByActiveSessionTokenHash(
            "a".repeat(64),
            activeAt
        ).orElseThrow();
        assertThat(currentUser.id()).isEqualTo(user.id());
        assertThat(currentUser.email()).isEqualTo(email);
        assertThat(currentUser.roles()).containsExactly("COMMENTER");
        assertThat(currentUser.createdAt()).isEqualTo(user.createdAt());
        assertThat(currentUser.updatedAt()).isEqualTo(user.updatedAt());

        SessionRevocationResult revoked = userAccountRepository.revokeSession(
            "a".repeat(64),
            activeAt
        );
        assertThat(revoked).isEqualTo(SessionRevocationResult.REVOKED);

        Integer revokedSessions = jdbcTemplate.queryForObject(
            "select count(*) from auth_sessions where user_id = ? and token_hash = ? and revoked_at is not null",
            Integer.class,
            user.id(),
            "a".repeat(64)
        );
        assertThat(revokedSessions).isOne();
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "a".repeat(64),
            alreadyRevokedAt
        )).isEmpty();

        SessionRevocationResult alreadyRevoked = userAccountRepository.revokeSession(
            "a".repeat(64),
            alreadyRevokedAt
        );
        assertThat(alreadyRevoked).isEqualTo(SessionRevocationResult.ALREADY_REVOKED);

        SessionRevocationResult missing = userAccountRepository.revokeSession(
            "b".repeat(64),
            activeAt
        );
        assertThat(missing).isEqualTo(SessionRevocationResult.NOT_FOUND_OR_EXPIRED);
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "b".repeat(64),
            activeAt
        )).isEmpty();

        userAccountRepository.createSession(user.id(), "c".repeat(64), expiresAt);
        SessionRevocationResult skewSafeRevoked = userAccountRepository.revokeSession(
            "c".repeat(64),
            skewedPastAt
        );
        assertThat(skewSafeRevoked).isEqualTo(SessionRevocationResult.REVOKED);

        Integer skewSafeSessions = jdbcTemplate.queryForObject(
            """
                select count(*)
                from auth_sessions
                where user_id = ?
                  and token_hash = ?
                  and revoked_at is not null
                  and revoked_at >= created_at
                """,
            Integer.class,
            user.id(),
            "c".repeat(64)
        );
        assertThat(skewSafeSessions).isOne();

        userAccountRepository.createSession(user.id(), "d".repeat(64), expiresAt);
        SessionRevocationResult expired = userAccountRepository.revokeSession(
            "d".repeat(64),
            expiredAt
        );
        assertThat(expired).isEqualTo(SessionRevocationResult.NOT_FOUND_OR_EXPIRED);
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "d".repeat(64),
            expiredAt
        )).isEmpty();

        String disabledEmail = "disabled-" + UUID.randomUUID() + "@example.com";
        var disabledUser = userAccountRepository.create(disabledEmail, "hashed-password", Set.of("COMMENTER"));
        userAccountRepository.createSession(disabledUser.id(), "e".repeat(64), expiresAt);
        jdbcTemplate.update("update app_users set is_enabled = false where id = ?", disabledUser.id());
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "e".repeat(64),
            activeAt
        )).isEmpty();
    }
}
