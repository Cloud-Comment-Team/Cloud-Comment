package com.cloudcomment.automoderation.persistence;

import com.cloudcomment.automoderation.domain.ActiveAutoModerationPolicy;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyConfig;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyVersion;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutoModerationPolicyRepository {

    void initializeFromLegacy(UUID siteId);

    Optional<ActiveAutoModerationPolicy> findActive(UUID siteId);

    Optional<AutoModerationPolicyVersion> findDraft(UUID siteId);

    Optional<AutoModerationPolicyVersion> findById(UUID siteId, UUID policyId);

    List<AutoModerationPolicyVersion> findPublished(UUID siteId);

    AutoModerationPolicyVersion createDraft(
        UUID siteId,
        UUID createdBy,
        UUID basedOnVersionId,
        boolean enabled,
        AutoModerationPreset preset,
        AutoModerationExecutionMode executionMode,
        AutoModerationPolicyConfig config
    );

    Optional<AutoModerationPolicyVersion> updateDraft(
        UUID siteId,
        UUID policyId,
        int expectedRevision,
        boolean enabled,
        AutoModerationPreset preset,
        AutoModerationExecutionMode executionMode,
        AutoModerationPolicyConfig config
    );

    boolean deleteDraft(UUID siteId, UUID policyId, int expectedRevision);

    Optional<AutoModerationPolicyState> lockState(UUID siteId);

    Optional<AutoModerationPolicyVersion> publishDraft(
        UUID siteId,
        UUID policyId,
        int expectedRevision,
        int version
    );

    AutoModerationPolicyVersion copyPublishedVersion(
        UUID siteId,
        UUID sourcePolicyId,
        UUID createdBy,
        int version
    );

    boolean activate(
        UUID siteId,
        UUID expectedActiveVersionId,
        AutoModerationPolicyVersion policy,
        int lastPublishedVersion
    );

    void synchronizeLegacySettings(UUID siteId, AutoModerationPolicyVersion policy);
}
