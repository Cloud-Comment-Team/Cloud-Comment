package com.cloudcomment.auth.persistence;

import com.cloudcomment.auth.domain.SessionAudience;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.widgetcontext.application.WidgetBootstrapResult;
import com.cloudcomment.widgetcontext.application.WidgetContextService;
import com.cloudcomment.widgetcontext.persistence.WidgetContextRepository;
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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.List;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    @Autowired
    WidgetContextRepository widgetContextRepository;

    @Autowired
    WidgetContextService widgetContextService;

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
                'automod_decision_events',
                'widget_bootstrap_tickets',
                'widget_frame_contexts'
            )
            """, Integer.class);
        Integer roleRows = jdbcTemplate.queryForObject("""
            select count(*)
            from roles
            where name in ('OWNER', 'COMMENTER', 'MODERATOR')
            """, Integer.class);
        Integer widgetExpiryIndexes = jdbcTemplate.queryForObject("""
            select count(*)
            from pg_indexes
            where schemaname = 'public'
              and indexname in (
                'idx_widget_bootstrap_tickets_expires_at',
                'idx_widget_frame_contexts_expires_at'
              )
            """, Integer.class);

        assertThat(databaseVersion).contains("PostgreSQL");
        assertThat(schemaHistoryRows).isEqualTo(19);
        assertThat(smokeTableRows).isZero();
        assertThat(coreTableRows).isEqualTo(20);
        assertThat(roleRows).isEqualTo(3);
        assertThat(widgetExpiryIndexes).isEqualTo(2);
    }

    @Test
    void v17ConsumesBootstrapTicketAtomicallyAndStoresOnlyHashedContextToken() throws Exception {
        var user = userAccountRepository.create(
            "widget-ticket-" + UUID.randomUUID() + "@example.com",
            "hash",
            Set.of("COMMENTER")
        );
        UUID siteId = createSite(user.id(), "atomic");
        String ticketHash = randomHash();
        String contextTokenHash = randomHash();
        String canonicalPageUrl = "https://atomic-" + UUID.randomUUID() + ".example.com/article";
        String origin = canonicalPageUrl.substring(0, canonicalPageUrl.lastIndexOf('/'));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        widgetContextRepository.createBootstrapTicket(
            ticketHash,
            siteId,
            origin,
            canonicalPageUrl,
            randomHash(),
            "A".repeat(43),
            new byte[] {1, 2, 3},
            now,
            now.plus(Duration.ofMinutes(2))
        );

        int attempts = 12;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Boolean>> results = IntStream.range(0, attempts)
                .mapToObj(ignored -> executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return widgetContextRepository.consumeBootstrapTicket(ticketHash, siteId, now.plusSeconds(1));
                }))
                .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(results.stream().filter(future -> get(future)).count()).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(widgetContextRepository.findActiveBootstrapTicket(ticketHash, siteId, now.plusSeconds(1)))
            .isEmpty();
        widgetContextRepository.deleteBootstrapTicket(ticketHash, siteId);
        widgetContextRepository.createFrameContext(
            contextTokenHash,
            siteId,
            origin,
            randomHash(),
            now,
            now.plus(Duration.ofHours(2))
        );
        assertThat(widgetContextRepository.findActiveFrameContext(contextTokenHash, siteId, now.plusSeconds(1)))
            .hasValueSatisfying(context -> {
                assertThat(context.siteId()).isEqualTo(siteId);
                assertThat(context.origin()).isEqualTo(origin);
                assertThat(context.expiresAt()).isEqualTo(now.plus(Duration.ofHours(2)));
            });
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'widget_frame_contexts' and column_name = 'canonical_page_url'",
            Integer.class
        )).isZero();
    }

    @Test
    void v17RetentionKeepsSiteScopedSessionsAndRollbackShapeFailsClosedInBackend() {
        var user = userAccountRepository.create(
            "widget-retention-" + UUID.randomUUID() + "@example.com",
            "hash",
            Set.of("COMMENTER")
        );
        UUID siteId = createSite(user.id(), "retention");
        String origin = "https://retention-" + UUID.randomUUID() + ".example.com";
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String contextHash = randomHash();
        widgetContextRepository.createFrameContext(
            contextHash,
            siteId,
            origin,
            randomHash(),
            now.minus(Duration.ofHours(50)),
            now.minus(Duration.ofHours(49))
        );
        String scopedSessionHash = randomHash();
        userAccountRepository.createSession(
            user.id(),
            scopedSessionHash,
            SessionAudience.WIDGET,
            siteId,
            origin,
            now.plus(Duration.ofHours(1))
        );
        widgetContextRepository.createBootstrapTicket(
            randomHash(),
            siteId,
            origin,
            origin + "/article",
            randomHash(),
            "B".repeat(43),
            new byte[] {4, 5, 6},
            now.minus(Duration.ofHours(50)),
            now.minus(Duration.ofHours(49))
        );

        Instant cutoff = now.minus(Duration.ofHours(24));
        assertThat(widgetContextRepository.deleteExpiredBootstrapTickets(cutoff)).isOne();
        assertThat(widgetContextRepository.deleteExpiredFrameContexts(cutoff)).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from auth_sessions where token_hash = ?",
            Integer.class,
            scopedSessionHash
        )).isOne();
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            scopedSessionHash,
            SessionAudience.WIDGET,
            siteId,
            origin,
            now
        )).isPresent();

        String rollbackTokenHash = randomHash();
        jdbcTemplate.update(
            "insert into auth_sessions (user_id, token_hash, audience, expires_at) values (?, ?, 'WIDGET', ?)",
            user.id(),
            rollbackTokenHash,
            java.time.OffsetDateTime.ofInstant(now.plus(Duration.ofHours(1)), ZoneOffset.UTC)
        );
        assertThatThrownBy(() -> userAccountRepository.findUserByActiveSessionTokenHash(
            rollbackTokenHash,
            SessionAudience.WIDGET,
            now
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "insert into auth_sessions (user_id, token_hash, audience, site_id, expires_at) values (?, ?, 'WIDGET', ?, ?)",
            user.id(),
            randomHash(),
            siteId,
            java.time.OffsetDateTime.ofInstant(now.plus(Duration.ofHours(1)), ZoneOffset.UTC)
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void widgetContextServiceExchangesOneRealTicketExactlyOnceUnderConcurrency() throws Exception {
        var user = userAccountRepository.create(
            "widget-exchange-" + UUID.randomUUID() + "@example.com",
            "hash",
            Set.of("COMMENTER")
        );
        String embeddingOrigin = "https://exchange-" + UUID.randomUUID() + ".example.com";
        UUID siteId = createSite(user.id(), "exchange");
        jdbcTemplate.update(
            "insert into site_allowed_origins (site_id, origin) values (?, ?)",
            siteId,
            embeddingOrigin
        );
        BootstrapProof bootstrap = createBootstrapProof(siteId, embeddingOrigin);
        int attempts = 12;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Boolean>> results = IntStream.range(0, attempts)
                .mapToObj(ignored -> executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        widgetContextService.exchange(
                            siteId,
                            "http://widget.localhost",
                            bootstrap.result().ticket(),
                            bootstrap.proof()
                        );
                        return true;
                    } catch (ApplicationException exception) {
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.INVALID_WIDGET_BOOTSTRAP);
                        return false;
                    }
                }))
                .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(results.stream().filter(this::get).count()).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from widget_frame_contexts where site_id = ?",
            Integer.class,
            siteId
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from widget_bootstrap_tickets where site_id = ?",
            Integer.class,
            siteId
        )).isZero();
        assertThatThrownBy(() -> widgetContextService.exchange(
            siteId,
            "http://widget.localhost",
            bootstrap.result().ticket(),
            bootstrap.proof()
        )).isInstanceOfSatisfying(ApplicationException.class, exception ->
            assertThat(exception.code()).isEqualTo(ApiErrorCode.INVALID_WIDGET_BOOTSTRAP)
        );
    }

    @Test
    void bootstrapAllowsOnlyOneOutstandingTicketPerSiteOriginAndKey() throws Exception {
        var user = userAccountRepository.create(
            "widget-bootstrap-" + UUID.randomUUID() + "@example.com",
            "hash",
            Set.of("COMMENTER")
        );
        String embeddingOrigin = "https://bootstrap-" + UUID.randomUUID() + ".example.com";
        UUID siteId = createSite(user.id(), "bootstrap-limit");
        jdbcTemplate.update(
            "insert into site_allowed_origins (site_id, origin) values (?, ?)",
            siteId,
            embeddingOrigin
        );
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String publicKey = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        WidgetBootstrapResult first = widgetContextService.bootstrap(
            siteId,
            embeddingOrigin,
            embeddingOrigin + "/article",
            publicKey
        );

        assertThatThrownBy(() -> widgetContextService.bootstrap(
            siteId,
            embeddingOrigin,
            embeddingOrigin + "/article",
            publicKey
        )).isInstanceOfSatisfying(ApplicationException.class, exception ->
            assertThat(exception.code()).isEqualTo(ApiErrorCode.RATE_LIMITED)
        );
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from widget_bootstrap_tickets where site_id = ? and consumed_at is null",
            Integer.class,
            siteId
        )).isOne();

        jdbcTemplate.update(
            "update widget_bootstrap_tickets set created_at = now() - interval '2 seconds', expires_at = now() - interval '1 second' where site_id = ?",
            siteId
        );
        WidgetBootstrapResult replacement = widgetContextService.bootstrap(
            siteId,
            embeddingOrigin,
            embeddingOrigin + "/article",
            publicKey
        );
        assertThat(replacement.ticket()).isNotEqualTo(first.ticket());
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from widget_bootstrap_tickets where site_id = ? and consumed_at is null",
            Integer.class,
            siteId
        )).isOne();
    }

    @Test
    void failedContextInsertRollsBackTicketConsumptionAndAllowsSafeRetry() throws Exception {
        var user = userAccountRepository.create(
            "widget-rollback-" + UUID.randomUUID() + "@example.com",
            "hash",
            Set.of("COMMENTER")
        );
        String embeddingOrigin = "https://rollback-" + UUID.randomUUID() + ".example.com";
        UUID siteId = createSite(user.id(), "exchange-rollback");
        jdbcTemplate.update(
            "insert into site_allowed_origins (site_id, origin) values (?, ?)",
            siteId,
            embeddingOrigin
        );
        BootstrapProof bootstrap = createBootstrapProof(siteId, embeddingOrigin);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String functionName = "reject_widget_context_" + suffix;
        String triggerName = "reject_widget_context_trigger_" + suffix;
        jdbcTemplate.execute("""
            create function %s() returns trigger language plpgsql as $$
            begin
                raise exception 'intentional widget context insert failure';
            end
            $$
            """.formatted(functionName));
        jdbcTemplate.execute("""
            create trigger %s before insert on widget_frame_contexts
            for each row when (new.site_id = '%s'::uuid)
            execute function %s()
            """.formatted(triggerName, siteId, functionName));
        try {
            assertThatThrownBy(() -> widgetContextService.exchange(
                siteId,
                "http://widget.localhost",
                bootstrap.result().ticket(),
                bootstrap.proof()
            )).isInstanceOf(DataAccessException.class);
        } finally {
            jdbcTemplate.execute("drop trigger if exists " + triggerName + " on widget_frame_contexts");
            jdbcTemplate.execute("drop function if exists " + functionName + "()");
        }

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from widget_bootstrap_tickets where site_id = ? and consumed_at is null",
            Integer.class,
            siteId
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from widget_frame_contexts where site_id = ?",
            Integer.class,
            siteId
        )).isZero();

        assertThat(widgetContextService.exchange(
            siteId,
            "http://widget.localhost",
            bootstrap.result().ticket(),
            bootstrap.proof()
        ).contextToken()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from widget_bootstrap_tickets where site_id = ?",
            Integer.class,
            siteId
        )).isZero();
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
    void v16AndV18RevokeLegacyWidgetSessionsAndKeepRollbackCompatibleShape() {
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

            jdbcTemplate.update(
                "insert into " + schema + ".auth_sessions (user_id, token_hash, audience, expires_at) values (?, ?, 'WIDGET', now() + interval '1 day')",
                userId,
                "1".repeat(64)
            );

            Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("18"))
                .load()
                .migrate();

            assertThat(jdbcTemplate.queryForObject(
                "select audience = 'LEGACY' and revoked_at is not null from " + schema + ".auth_sessions where token_hash = ?",
                Boolean.class,
                "1".repeat(64)
            )).isTrue();
            jdbcTemplate.update(
                "insert into " + schema + ".auth_sessions (user_id, token_hash, audience, expires_at) values (?, ?, 'WIDGET', now() + interval '1 day')",
                userId,
                "2".repeat(64)
            );
            assertThat(jdbcTemplate.queryForObject(
                "select site_id is null and origin is null from "
                    + schema + ".auth_sessions where token_hash = ?",
                Boolean.class,
                "2".repeat(64)
            )).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = ? and table_name = 'widget_frame_contexts' and column_name = 'canonical_page_url'",
                Integer.class,
                schema
            )).isZero();
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = ? and table_name = 'widget_bootstrap_tickets' and column_name = 'canonical_page_url'",
                Integer.class,
                schema
            )).isOne();
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = ? and table_name = 'auth_sessions' and column_name = 'widget_context_id'",
                Integer.class,
                schema
            )).isZero();
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

        String widgetOrigin = "https://widget-scope.example.com";
        UUID widgetSiteId = jdbcTemplate.queryForObject(
            "insert into sites (owner_id, name, domain, public_key) values (?, 'Widget scope', ?, ?) returning id",
            UUID.class,
            user.id(),
            "widget-scope-" + UUID.randomUUID() + ".example.com",
            UUID.randomUUID().toString().replace("-", "").repeat(2).substring(0, 64)
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
            "insert into auth_sessions (user_id, token_hash, audience, site_id, expires_at) values (?, ?, 'WIDGET_FRAME', ?, ?)",
            user.id(),
            "9".repeat(64),
            widgetSiteId,
            java.time.OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        )).isInstanceOf(DataAccessException.class);

        SessionRevocationResult wrongAudienceRevoke = userAccountRepository.revokeSession(
            "a".repeat(64),
            SessionAudience.WIDGET,
            widgetSiteId,
            widgetOrigin,
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

        userAccountRepository.createSession(
            user.id(),
            "c".repeat(64),
            SessionAudience.WIDGET,
            widgetSiteId,
            widgetOrigin,
            expiresAt
        );
        assertThat(jdbcTemplate.queryForObject(
            "select audience from auth_sessions where token_hash = ?",
            String.class,
            "c".repeat(64)
        )).isEqualTo("WIDGET_FRAME");
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from auth_sessions where token_hash = ? and audience = 'WIDGET'",
            Integer.class,
            "c".repeat(64)
        )).as("предыдущая версия backend не должна видеть scoped-сессию как WIDGET").isZero();
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "c".repeat(64),
            SessionAudience.WIDGET,
            widgetSiteId,
            widgetOrigin,
            activeAt
        )).isPresent();
        assertThat(userAccountRepository.findUserByActiveSessionTokenHash(
            "c".repeat(64),
            SessionAudience.WIDGET,
            widgetSiteId,
            "https://other-widget-scope.example.com",
            activeAt
        )).isEmpty();
        assertThat(userAccountRepository.revokeSession(
            "c".repeat(64),
            SessionAudience.ADMIN,
            activeAt
        )).isEqualTo(SessionRevocationResult.NOT_FOUND_OR_EXPIRED);
        SessionRevocationResult skewSafeRevoked = userAccountRepository.revokeSession(
            "c".repeat(64),
            SessionAudience.WIDGET,
            widgetSiteId,
            widgetOrigin,
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

    private UUID createSite(UUID ownerId, String prefix) {
        return jdbcTemplate.queryForObject(
            "insert into sites (owner_id, name, domain, public_key) values (?, ?, ?, ?) returning id",
            UUID.class,
            ownerId,
            "Widget " + prefix,
            prefix + "-" + UUID.randomUUID() + ".example.com",
            randomHash()
        );
    }

    private BootstrapProof createBootstrapProof(UUID siteId, String origin) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        String pageUrl = origin + "/article";
        WidgetBootstrapResult result = widgetContextService.bootstrap(
            siteId,
            origin,
            pageUrl,
            Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPublic().getEncoded())
        );
        String payload = "CLOUDCOMMENT_WIDGET_BOOTSTRAP_V1\n" + siteId + "\n" + origin + "\n"
            + result.canonicalPageUrl() + "\n" + result.publicKeyFingerprint() + "\n" + result.ticket();
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return new BootstrapProof(
            result,
            Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())
        );
    }

    private String randomHash() {
        return UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean get(Future<Boolean> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent ticket consumption did not complete", exception);
        }
    }

    private record BootstrapProof(WidgetBootstrapResult result, String proof) {
    }
}
