package com.cloudcomment.persistence;

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
                'moderation_actions'
            )
            """, Integer.class);
        Integer roleRows = jdbcTemplate.queryForObject("""
            select count(*)
            from roles
            where name in ('OWNER', 'COMMENTER', 'MODERATOR')
            """, Integer.class);

        assertThat(databaseVersion).contains("PostgreSQL");
        assertThat(schemaHistoryRows).isEqualTo(4);
        assertThat(smokeTableRows).isZero();
        assertThat(coreTableRows).isEqualTo(9);
        assertThat(roleRows).isEqualTo(3);
    }

    @Test
    void repositoryCreatesUsersReadsCredentialsAndStoresSessions() {
        String email = "repo-" + UUID.randomUUID() + "@example.com";
        Instant expiresAt = Instant.parse("2026-07-01T12:00:00Z");

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
    }
}
