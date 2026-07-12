package com.cloudcomment.automoderation.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.automoderation.domain.ActiveAutoModerationPolicy;
import com.cloudcomment.automoderation.domain.AutoModerationCleanAction;
import com.cloudcomment.automoderation.domain.AutoModerationDecisionType;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationLinkAction;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyConfig;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyLifecycle;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyVersion;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;
import com.cloudcomment.automoderation.persistence.AutoModerationPolicyRepository;
import com.cloudcomment.automoderation.persistence.AutoModerationPolicyState;
import com.cloudcomment.comment.application.AutoModerationService;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoModerationPolicyServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");

    private AutoModerationPolicyRepository repository;
    private AutoModerationPolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(AutoModerationPolicyRepository.class);
        ResourceOwnershipService ownership = new ResourceOwnershipService(
            (ownerId, resourceType, resourceId) -> true
        );
        service = new AutoModerationPolicyService(
            repository,
            ownership,
            new AutoModerationService(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void openAndBalancedApproveCleanPreModeratedCommentsWhileStrictFollowsSiteMode() {
        UUID siteId = UUID.randomUUID();

        when(repository.findActive(siteId)).thenReturn(Optional.of(active(policy(
            siteId, AutoModerationPreset.OPEN, AutoModerationExecutionMode.LIVE,
            AutoModerationPolicyConfig.preset(AutoModerationPreset.OPEN)
        ))));
        assertThat(service.evaluateForComment(
            siteId, "Спасибо за полезный материал", CommentStatus.PENDING, CommentStatus.PENDING
        ).effectiveStatus()).isEqualTo(CommentStatus.APPROVED);

        when(repository.findActive(siteId)).thenReturn(Optional.of(active(policy(
            siteId, AutoModerationPreset.BALANCED, AutoModerationExecutionMode.LIVE,
            AutoModerationPolicyConfig.preset(AutoModerationPreset.BALANCED)
        ))));
        assertThat(service.evaluateForComment(
            siteId, "Спасибо за полезный материал", CommentStatus.PENDING, CommentStatus.PENDING
        ).effectiveStatus()).isEqualTo(CommentStatus.APPROVED);

        when(repository.findActive(siteId)).thenReturn(Optional.of(active(policy(
            siteId, AutoModerationPreset.STRICT, AutoModerationExecutionMode.LIVE,
            AutoModerationPolicyConfig.preset(AutoModerationPreset.STRICT)
        ))));
        assertThat(service.evaluateForComment(
            siteId, "Спасибо за полезный материал", CommentStatus.PENDING, CommentStatus.PENDING
        ).effectiveStatus()).isEqualTo(CommentStatus.PENDING);
    }

    @Test
    void shadowStoresSafeDecisionButPreservesCurrentEditStatus() {
        UUID siteId = UUID.randomUUID();
        AutoModerationPolicyConfig config = new AutoModerationPolicyConfig(
            45, 90, AutoModerationCleanAction.APPROVE, AutoModerationLinkAction.REVIEW, 2,
            List.of("секретное-слово")
        );
        when(repository.findActive(siteId)).thenReturn(Optional.of(active(policy(
            siteId, AutoModerationPreset.CUSTOM, AutoModerationExecutionMode.SHADOW, config
        ))));

        AutoModerationEvaluation evaluation = service.evaluateForComment(
            siteId,
            "Текст содержит секретное-слово",
            CommentStatus.PENDING,
            CommentStatus.APPROVED
        );

        assertThat(evaluation.decision()).isEqualTo(AutoModerationDecisionType.SPAM);
        assertThat(evaluation.effectiveStatus()).isEqualTo(CommentStatus.APPROVED);
        assertThat(evaluation.applied()).isFalse();
        assertThat(evaluation.reason()).doesNotContain("секретное-слово");
        assertThat(evaluation.safeSignals()).allSatisfy(signal ->
            assertThat(signal).hasNoNullFieldsOrProperties()
        );
    }

    @Test
    void linkActionAppliesOnlyAboveConfiguredLimit() {
        UUID siteId = UUID.randomUUID();
        AutoModerationPolicyConfig config = new AutoModerationPolicyConfig(
            45, 90, AutoModerationCleanAction.APPROVE, AutoModerationLinkAction.REVIEW, 1, List.of()
        );
        when(repository.findActive(siteId)).thenReturn(Optional.of(active(policy(
            siteId, AutoModerationPreset.CUSTOM, AutoModerationExecutionMode.LIVE, config
        ))));

        AutoModerationEvaluation withinLimit = service.evaluateForComment(
            siteId, "https://example.com", CommentStatus.APPROVED, CommentStatus.APPROVED
        );
        AutoModerationEvaluation aboveLimit = service.evaluateForComment(
            siteId,
            "https://one.example.com https://two.example.com",
            CommentStatus.APPROVED,
            CommentStatus.APPROVED
        );

        assertThat(withinLimit.decision()).isEqualTo(AutoModerationDecisionType.APPROVE);
        assertThat(aboveLimit.decision()).isEqualTo(AutoModerationDecisionType.REVIEW);
        assertThat(aboveLimit.effectiveStatus()).isEqualTo(CommentStatus.PENDING);
    }

    @Test
    void customPoliciesKeepSignalProfileImpliedByLegacyThresholds() {
        UUID siteId = UUID.randomUUID();
        AutoModerationPolicyConfig relaxed = new AutoModerationPolicyConfig(
            70, 130, AutoModerationCleanAction.APPROVE, AutoModerationLinkAction.ALLOW, 2, List.of()
        );
        when(repository.findActive(siteId)).thenReturn(Optional.of(active(policy(
            siteId, AutoModerationPreset.CUSTOM, AutoModerationExecutionMode.LIVE, relaxed
        ))));

        AutoModerationEvaluation evaluation = service.evaluateForComment(
            siteId, "This looks like a scam", CommentStatus.APPROVED, CommentStatus.APPROVED
        );

        assertThat(evaluation.score()).isEqualTo(25);
        assertThat(evaluation.decision()).isEqualTo(AutoModerationDecisionType.APPROVE);
    }

    @Test
    void creatingCustomDraftFromActiveCustomPolicyKeepsItsConfiguration() {
        UUID siteId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AutoModerationPolicyConfig config = new AutoModerationPolicyConfig(
            33, 77, AutoModerationCleanAction.FOLLOW_SITE_MODE, AutoModerationLinkAction.SPAM, 4,
            List.of("слово")
        );
        AutoModerationPolicyVersion active = policy(
            siteId, AutoModerationPreset.CUSTOM, AutoModerationExecutionMode.SHADOW, config
        );
        when(repository.findDraft(siteId)).thenReturn(Optional.empty());
        when(repository.findActive(siteId)).thenReturn(Optional.of(active(active)));
        when(repository.lockState(siteId)).thenReturn(Optional.of(new AutoModerationPolicyState(
            siteId, active.id(), true, AutoModerationExecutionMode.SHADOW, 1, "a".repeat(64)
        )));
        when(repository.createDraft(
            eq(siteId), eq(ownerId), eq(active.id()), eq(true), eq(AutoModerationPreset.CUSTOM),
            eq(AutoModerationExecutionMode.SHADOW), eq(config)
        )).thenReturn(draft(siteId, active.id(), config));

        service.createDraft(user(ownerId), siteId, AutoModerationPreset.CUSTOM, true, AutoModerationExecutionMode.SHADOW);

        verify(repository).createDraft(
            siteId, ownerId, active.id(), true, AutoModerationPreset.CUSTOM,
            AutoModerationExecutionMode.SHADOW, config
        );
    }

    @Test
    void staleExpectedActiveVersionFailsBeforePublishing() {
        UUID siteId = UUID.randomUUID();
        AutoModerationPolicyVersion active = policy(
            siteId, AutoModerationPreset.BALANCED, AutoModerationExecutionMode.LIVE,
            AutoModerationPolicyConfig.preset(AutoModerationPreset.BALANCED)
        );
        AutoModerationPolicyVersion draft = draft(siteId, active.id(), active.config());
        when(repository.findActive(siteId)).thenReturn(Optional.of(active(active)));
        when(repository.lockState(siteId)).thenReturn(Optional.of(new AutoModerationPolicyState(
            siteId, active.id(), true, AutoModerationExecutionMode.LIVE, 2, "a".repeat(64)
        )));

        assertThatThrownBy(() -> service.publish(
            user(UUID.randomUUID()), siteId, draft.id(), draft.revision(), UUID.randomUUID()
        ))
            .isInstanceOf(ApplicationException.class)
            .extracting("code")
            .hasToString("BUSINESS_ERROR");
    }

    private ActiveAutoModerationPolicy active(AutoModerationPolicyVersion policy) {
        return new ActiveAutoModerationPolicy(policy, policy.enabled(), policy.executionMode());
    }

    private AutoModerationPolicyVersion policy(
        UUID siteId,
        AutoModerationPreset preset,
        AutoModerationExecutionMode mode,
        AutoModerationPolicyConfig config
    ) {
        return new AutoModerationPolicyVersion(
            UUID.randomUUID(), siteId, 1, 1, AutoModerationPolicyLifecycle.PUBLISHED, true,
            preset, mode, config, null, NOW, NOW, NOW
        );
    }

    private AutoModerationPolicyVersion draft(
        UUID siteId,
        UUID basedOn,
        AutoModerationPolicyConfig config
    ) {
        return new AutoModerationPolicyVersion(
            UUID.randomUUID(), siteId, null, 1, AutoModerationPolicyLifecycle.DRAFT, true,
            AutoModerationPreset.CUSTOM, AutoModerationExecutionMode.SHADOW,
            config, basedOn, NOW, NOW, null
        );
    }

    private com.cloudcomment.auth.application.AuthenticatedUser user(UUID id) {
        return new com.cloudcomment.auth.application.AuthenticatedUser(
            id, id + "@example.com", Set.of("OWNER"), NOW, NOW
        );
    }
}
