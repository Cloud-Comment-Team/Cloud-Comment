package com.cloudcomment.automoderation.application;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;
import com.cloudcomment.automoderation.persistence.AutoModerationPolicyRepository;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AutoModerationPolicyIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AutoModerationPolicyRepository repository;

    @Autowired
    private AutoModerationPolicyService service;

    @Test
    void publishesDraftRollsBackAsNewVersionAndRejectsStaleActivePointer() {
        Fixture fixture = fixture();
        AutoModerationPolicySet initial = service.list(fixture.user(), fixture.siteId());
        var draft = service.createDraft(
            fixture.user(), fixture.siteId(), AutoModerationPreset.STRICT, true, AutoModerationExecutionMode.SHADOW
        );

        var published = service.publish(
            fixture.user(), fixture.siteId(), draft.id(), draft.revision(), initial.activePolicy().id()
        );
        assertThat(published.version()).isEqualTo(2);
        assertThat(published.preset()).isEqualTo(AutoModerationPreset.STRICT);
        assertThat(published.executionMode()).isEqualTo(AutoModerationExecutionMode.SHADOW);
        assertThat(jdbcTemplate.queryForObject(
            "select automod_enabled from sites where id = ?", Boolean.class, fixture.siteId()
        )).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "select automod_strictness from sites where id = ?", String.class, fixture.siteId()
        )).isEqualTo("OFF");

        var restored = service.rollback(
            fixture.user(), fixture.siteId(), initial.activePolicy().id(), published.id()
        );
        assertThat(restored.version()).isEqualTo(3);
        assertThat(restored.basedOnVersionId()).isEqualTo(initial.activePolicy().id());
        assertThat(jdbcTemplate.queryForObject(
            "select automod_enabled from sites where id = ?", Boolean.class, fixture.siteId()
        )).isTrue();
        assertThat(service.list(fixture.user(), fixture.siteId()).activePolicy().id()).isEqualTo(restored.id());

        var nextDraft = service.createDraft(
            fixture.user(), fixture.siteId(), AutoModerationPreset.BALANCED, true, AutoModerationExecutionMode.LIVE
        );
        assertThatThrownBy(() -> service.publish(
            fixture.user(), fixture.siteId(), nextDraft.id(), nextDraft.revision(), published.id()
        ))
            .isInstanceOf(ApplicationException.class)
            .extracting("code")
            .hasToString("BUSINESS_ERROR");

        service.deleteDraft(fixture.user(), fixture.siteId(), nextDraft.id(), nextDraft.revision());
        assertThat(service.list(fixture.user(), fixture.siteId()).draft()).isNull();
    }

    @Test
    void reconcilesLegacyColumnsChangedByPreviousReleaseWithoutRewritingHistory() {
        Fixture fixture = fixture();
        AutoModerationPolicySet initial = service.list(fixture.user(), fixture.siteId());

        jdbcTemplate.update(
            """
                update sites
                set automod_enabled = true,
                    automod_strictness = 'RELAXED',
                    automod_blocked_words = 'legacy-word',
                    automod_hold_links = false,
                    automod_block_links = false,
                    automod_max_links = 5
                where id = ?
                """,
            fixture.siteId()
        );

        AutoModerationPolicySet reconciled = service.list(fixture.user(), fixture.siteId());

        assertThat(reconciled.activePolicy().id()).isNotEqualTo(initial.activePolicy().id());
        assertThat(reconciled.activePolicy().version()).isEqualTo(2);
        assertThat(reconciled.activePolicy().preset()).isEqualTo(AutoModerationPreset.CUSTOM);
        assertThat(reconciled.activePolicy().config().blockedWords()).containsExactly("legacy-word");
        assertThat(reconciled.activePolicy().basedOnVersionId()).isEqualTo(initial.activePolicy().id());
        assertThat(reconciled.versions()).extracting("id")
            .contains(initial.activePolicy().id(), reconciled.activePolicy().id());
    }

    @Test
    void legacyReconciliationMakesExistingDraftStale() {
        Fixture fixture = fixture();
        AutoModerationPolicySet initial = service.list(fixture.user(), fixture.siteId());
        var draft = service.createDraft(
            fixture.user(), fixture.siteId(), AutoModerationPreset.STRICT, true, AutoModerationExecutionMode.LIVE
        );
        jdbcTemplate.update(
            "update sites set automod_strictness = 'RELAXED' where id = ?",
            fixture.siteId()
        );
        AutoModerationPolicySet reconciled = service.list(fixture.user(), fixture.siteId());

        assertThat(reconciled.activePolicy().id()).isNotEqualTo(initial.activePolicy().id());
        assertThatThrownBy(() -> service.publish(
            fixture.user(), fixture.siteId(), draft.id(), draft.revision(), reconciled.activePolicy().id()
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Draft is based on an outdated active policy")
            .extracting("code")
            .hasToString("BUSINESS_ERROR");
    }

    @Test
    void foreignOwnerReceivesNotFound() {
        Fixture fixture = fixture();
        AuthenticatedUser foreign = user(UUID.randomUUID());

        assertThatThrownBy(() -> service.list(foreign, fixture.siteId()))
            .isInstanceOf(ApplicationException.class)
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    @Test
    void concurrentDraftCreationAndRollbackSerializeWithoutDeadlockOrStaleBase() throws Exception {
        Fixture fixture = fixture();
        AutoModerationPolicySet initial = service.list(fixture.user(), fixture.siteId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> create = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    service.createDraft(
                        fixture.user(), fixture.siteId(), AutoModerationPreset.STRICT,
                        true, AutoModerationExecutionMode.LIVE
                    );
                    return true;
                } catch (ApplicationException exception) {
                    return false;
                }
            });
            Future<Boolean> rollback = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    service.rollback(
                        fixture.user(), fixture.siteId(), initial.activePolicy().id(), initial.activePolicy().id()
                    );
                    return true;
                } catch (ApplicationException exception) {
                    return false;
                }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean createSucceeded = create.get(10, TimeUnit.SECONDS);
            boolean rollbackSucceeded = rollback.get(10, TimeUnit.SECONDS);
            AutoModerationPolicySet after = service.list(fixture.user(), fixture.siteId());

            assertThat(createSucceeded).isTrue();
            assertThat(after.draft()).isNotNull();
            assertThat(after.draft().basedOnVersionId()).isEqualTo(after.activePolicy().id());
            assertThat(after.versions().stream()
                .filter(version -> version.id().equals(after.activePolicy().id()))
                .count()).isOne();
            if (rollbackSucceeded) {
                assertThat(after.activePolicy().version()).isEqualTo(2);
            } else {
                assertThat(after.activePolicy().id()).isEqualTo(initial.activePolicy().id());
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Fixture fixture() {
        UUID ownerId = jdbcTemplate.queryForObject(
            "insert into app_users (email, password_hash) values (?, 'hash') returning id",
            UUID.class,
            UUID.randomUUID() + "@example.com"
        );
        UUID siteId = jdbcTemplate.queryForObject(
            "insert into sites (owner_id, name, domain, public_key) values (?, 'Site', ?, ?) returning id",
            UUID.class,
            ownerId,
            UUID.randomUUID() + ".example.com",
            "key-" + UUID.randomUUID()
        );
        repository.initializeFromLegacy(siteId);
        return new Fixture(siteId, user(ownerId));
    }

    private AuthenticatedUser user(UUID id) {
        Instant now = Instant.parse("2026-07-13T10:00:00Z");
        return new AuthenticatedUser(id, id + "@example.com", Set.of("OWNER"), now, now);
    }

    private record Fixture(UUID siteId, AuthenticatedUser user) {
    }
}
