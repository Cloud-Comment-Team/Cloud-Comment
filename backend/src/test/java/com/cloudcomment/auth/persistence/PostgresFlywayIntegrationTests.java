package com.cloudcomment.auth.persistence;

import com.cloudcomment.auth.domain.SessionAudience;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                'privacy_events',
                'owner_notifications',
                'site_widget_health',
                'automod_policy_versions',
                'site_automod_policy_state',
                'automod_policy_feedback',
                'automod_decision_events'
            )
            """, Integer.class);
        Integer roleRows = jdbcTemplate.queryForObject("""
            select count(*)
            from roles
            where name in ('OWNER', 'COMMENTER', 'MODERATOR')
            """, Integer.class);

        assertThat(databaseVersion).contains("PostgreSQL");
        assertThat(schemaHistoryRows).isEqualTo(18);
        assertThat(smokeTableRows).isZero();
        assertThat(coreTableRows).isEqualTo(18);
        assertThat(roleRows).isEqualTo(3);
    }

    @Test
    void v13UpgradesExistingV12SiteWithoutInventingHealthEvents() {
        String schema = "v13_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway v12 = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("12"))
                .load();
            v12.migrate();

            UUID ownerId = jdbcTemplate.queryForObject(
                "insert into " + schema + ".app_users (email, password_hash) values (?, ?) returning id",
                UUID.class,
                "upgrade-" + UUID.randomUUID() + "@example.com",
                "hash"
            );
            UUID siteId = jdbcTemplate.queryForObject(
                "insert into " + schema + ".sites (owner_id, name, domain, public_key) values (?, ?, ?, ?) returning id",
                UUID.class,
                ownerId,
                "Existing V12 site",
                "upgrade.example.com",
                "upgrade-" + UUID.randomUUID()
            );

            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("13"))
                .load()
                .migrate();

            Boolean migrationSucceeded = jdbcTemplate.queryForObject(
                "select success from " + schema + ".flyway_schema_history where version = '13'",
                Boolean.class
            );
            Integer existingSiteRows = jdbcTemplate.queryForObject(
                "select count(*) from " + schema + ".sites where id = ?",
                Integer.class,
                siteId
            );
            Integer healthRows = jdbcTemplate.queryForObject(
                "select count(*) from " + schema + ".site_widget_health where site_id = ?",
                Integer.class,
                siteId
            );

            assertThat(migrationSucceeded).isTrue();
            assertThat(existingSiteRows).isOne();
            assertThat(healthRows).isZero();
        } finally {
            jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void v14PreservesLegacyBlockedWordsAndMakesPublishedVersionImmutable() {
        String schema = "v14_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("13"))
                .load()
                .migrate();

            UUID ownerId = jdbcTemplate.queryForObject(
                "insert into " + schema + ".app_users (email, password_hash) values (?, 'hash') returning id",
                UUID.class,
                "upgrade-" + UUID.randomUUID() + "@example.com"
            );
            String eightyCharacters = "x".repeat(80);
            String blockedWords = String.join("\n", IntStream.range(0, 120)
                .mapToObj(index -> index == 0 ? eightyCharacters : "word-" + index)
                .toList());
            UUID siteId = jdbcTemplate.queryForObject(
                """
                    insert into %s.sites (
                        owner_id, name, domain, public_key, automod_blocked_words
                    ) values (?, 'Legacy policy', ?, ?, ?) returning id
                    """.formatted(schema),
                UUID.class,
                ownerId,
                UUID.randomUUID() + ".example.com",
                "key-" + UUID.randomUUID(),
                blockedWords
            );

            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("14"))
                .load()
                .migrate();

            UUID policyId = jdbcTemplate.queryForObject(
                "select id from " + schema + ".automod_policy_versions where site_id = ? and version = 1",
                UUID.class,
                siteId
            );
            assertThat(jdbcTemplate.queryForObject(
                "select jsonb_array_length(blocked_words) from " + schema + ".automod_policy_versions where id = ?",
                Integer.class,
                policyId
            )).isEqualTo(120);
            assertThat(jdbcTemplate.queryForObject(
                "select length(blocked_words ->> 0) from " + schema + ".automod_policy_versions where id = ?",
                Integer.class,
                policyId
            )).isEqualTo(80);
            assertThatThrownBy(() -> jdbcTemplate.update(
                "update " + schema + ".automod_policy_versions set review_threshold = 44 where id = ?",
                policyId
            )).isInstanceOf(DataAccessException.class);
        } finally {
            jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void v15BackfillsAutomodDecisionSnapshotWithoutCopyingCommentText() {
        String schema = "v15_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("13"))
                .load()
                .migrate();

            UUID ownerId = jdbcTemplate.queryForObject(
                "insert into " + schema + ".app_users (email, password_hash) values (?, 'hash') returning id",
                UUID.class,
                "v15-" + UUID.randomUUID() + "@example.com"
            );
            UUID siteId = jdbcTemplate.queryForObject(
                """
                    insert into %s.sites (owner_id, name, domain, public_key)
                    values (?, 'V15 site', ?, ?) returning id
                    """.formatted(schema),
                UUID.class,
                ownerId,
                UUID.randomUUID() + ".example.com",
                "key-" + UUID.randomUUID()
            );
            UUID pageId = jdbcTemplate.queryForObject(
                "insert into " + schema + ".pages (site_id, url) values (?, ?) returning id",
                UUID.class,
                siteId,
                "https://v15.example.com/" + UUID.randomUUID()
            );
            UUID commentId = jdbcTemplate.queryForObject(
                """
                    insert into %s.comments (page_id, author_user_id, body, status)
                    values (?, ?, 'Текст не должен попасть в событие', 'APPROVED') returning id
                    """.formatted(schema),
                UUID.class,
                pageId,
                ownerId
            );

            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("14"))
                .load()
                .migrate();

            UUID policyId = jdbcTemplate.queryForObject(
                "select active_policy_version_id from " + schema + ".site_automod_policy_state where site_id = ?",
                UUID.class,
                siteId
            );
            Instant evaluatedAt = Instant.parse("2026-07-11T10:15:00Z");
            jdbcTemplate.update(
                """
                    update %s.comments
                    set automod_policy_version_id = ?,
                        automod_execution_mode = 'LIVE',
                        automod_score = 12,
                        automod_decision = 'APPROVE',
                        automod_signals = '[]'::jsonb,
                        automod_reason = null,
                        automod_applied_status = 'APPROVED',
                        automod_evaluated_at = ?
                    where id = ?
                    """.formatted(schema),
                policyId,
                evaluatedAt.atOffset(ZoneOffset.UTC),
                commentId
            );

            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("15"))
                .load()
                .migrate();

            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from " + schema + ".automod_decision_events where comment_id = ?",
                Integer.class,
                commentId
            )).isOne();
            assertThat(jdbcTemplate.queryForObject(
                """
                    select count(*)
                    from information_schema.columns
                    where table_schema = ?
                      and table_name = 'automod_decision_events'
                      and column_name in ('body', 'content', 'signals', 'reason')
                    """,
                Integer.class,
                schema
            )).isZero();
        } finally {
            jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void v16BackfillsLegacyAudienceRevokesExistingSessionsAndKeepsRollbackDefault() {
        String schema = "v16_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("15"))
                .load()
                .migrate();

            UUID userId = jdbcTemplate.queryForObject(
                "insert into " + schema + ".app_users (email, password_hash) values (?, 'hash') returning id",
                UUID.class,
                "v16-" + UUID.randomUUID() + "@example.com"
            );
            jdbcTemplate.update(
                "insert into " + schema + ".auth_sessions (user_id, token_hash, expires_at) values (?, ?, now() + interval '1 day')",
                userId,
                "f".repeat(64)
            );
            jdbcTemplate.update(
                "insert into " + schema + ".auth_sessions (user_id, token_hash, created_at, expires_at) values (?, ?, now() - interval '2 days', now() - interval '1 day')",
                userId,
                "e".repeat(64)
            );

            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("16"))
                .load()
                .migrate();

            assertThat(jdbcTemplate.queryForObject(
                "select audience from " + schema + ".auth_sessions where token_hash = ?",
                String.class,
                "f".repeat(64)
            )).isEqualTo("LEGACY");
            assertThat(jdbcTemplate.queryForObject(
                "select revoked_at is not null from " + schema + ".auth_sessions where token_hash = ?",
                Boolean.class,
                "f".repeat(64)
            )).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                "select audience = 'LEGACY' and revoked_at is null from " + schema + ".auth_sessions where token_hash = ?",
                Boolean.class,
                "e".repeat(64)
            )).isTrue();

            jdbcTemplate.update(
                "insert into " + schema + ".auth_sessions (user_id, token_hash, expires_at) values (?, ?, now() + interval '1 day')",
                userId,
                "0".repeat(64)
            );
            assertThat(jdbcTemplate.queryForObject(
                "select audience from " + schema + ".auth_sessions where token_hash = ?",
                String.class,
                "0".repeat(64)
            )).isEqualTo("LEGACY");
        } finally {
            jdbcTemplate.execute("drop schema if exists " + schema + " cascade");
        }
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

        userAccountRepository.createSession(user.id(), "a".repeat(64), SessionAudience.ADMIN, expiresAt);

        Integer sessions = jdbcTemplate.queryForObject(
            "select count(*) from auth_sessions where user_id = ? and token_hash = ?",
            Integer.class,
            user.id(),
            "a".repeat(64)
        );
        assertThat(sessions).isOne();

        var currentUser = userAccountRepository.findUserByActiveSessionTokenHash(
            "a".repeat(64),
            SessionAudience.ADMIN,
            activeAt
        ).orElseThrow();
        assertThat(currentUser.id()).isEqualTo(user.id());
        assertThat(currentUser.email()).isEqualTo(email);
        assertThat(currentUser.roles()).containsExactly("COMMENTER");
        assertThat(currentUser.createdAt()).isEqualTo(user.createdAt());
        assertThat(currentUser.updatedAt()).isEqualTo(user.updatedAt());

        SessionRevocationResult wrongAudienceRevoke = userAccountRepository.revokeSession(
            "a".repeat(64),
            SessionAudience.WIDGET,
            activeAt
        );
        assertThat(wrongAudienceRevoke).isEqualTo(SessionRevocationResult.NOT_FOUND_OR_EXPIRED);
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "a".repeat(64),
            SessionAudience.ADMIN,
            activeAt
        )).isPresent();

        SessionRevocationResult revoked = userAccountRepository.revokeSession(
            "a".repeat(64),
            SessionAudience.ADMIN,
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
            SessionAudience.ADMIN,
            alreadyRevokedAt
        )).isEmpty();

        SessionRevocationResult alreadyRevoked = userAccountRepository.revokeSession(
            "a".repeat(64),
            SessionAudience.ADMIN,
            alreadyRevokedAt
        );
        assertThat(alreadyRevoked).isEqualTo(SessionRevocationResult.ALREADY_REVOKED);

        SessionRevocationResult missing = userAccountRepository.revokeSession(
            "b".repeat(64),
            SessionAudience.ADMIN,
            activeAt
        );
        assertThat(missing).isEqualTo(SessionRevocationResult.NOT_FOUND_OR_EXPIRED);
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "b".repeat(64),
            SessionAudience.ADMIN,
            activeAt
        )).isEmpty();

        userAccountRepository.createSession(user.id(), "c".repeat(64), SessionAudience.WIDGET, expiresAt);
        assertThat(userAccountRepository.revokeSession(
            "c".repeat(64),
            SessionAudience.ADMIN,
            activeAt
        )).isEqualTo(SessionRevocationResult.NOT_FOUND_OR_EXPIRED);
        SessionRevocationResult skewSafeRevoked = userAccountRepository.revokeSession(
            "c".repeat(64),
            SessionAudience.WIDGET,
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

        userAccountRepository.createSession(user.id(), "d".repeat(64), SessionAudience.ADMIN, expiresAt);
        SessionRevocationResult expired = userAccountRepository.revokeSession(
            "d".repeat(64),
            SessionAudience.ADMIN,
            expiredAt
        );
        assertThat(expired).isEqualTo(SessionRevocationResult.NOT_FOUND_OR_EXPIRED);
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "d".repeat(64),
            SessionAudience.ADMIN,
            expiredAt
        )).isEmpty();

        String disabledEmail = "disabled-" + UUID.randomUUID() + "@example.com";
        var disabledUser = userAccountRepository.create(disabledEmail, "hashed-password", Set.of("COMMENTER"));
        userAccountRepository.createSession(disabledUser.id(), "e".repeat(64), SessionAudience.ADMIN, expiresAt);
        jdbcTemplate.update("update app_users set is_enabled = false where id = ?", disabledUser.id());
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "e".repeat(64),
            SessionAudience.ADMIN,
            activeAt
        )).isEmpty();
    }
}
