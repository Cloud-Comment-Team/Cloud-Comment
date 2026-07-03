package com.cloudcomment.account.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PersonalDataSnapshot(
    AccountProfile account,
    List<String> roles,
    List<Consent> consents,
    Sessions sessions,
    Resources resources,
    DeletionRequest deletionRequest,
    Instant exportedAt
) {

    public PersonalDataSnapshot {
        roles = List.copyOf(roles);
        consents = List.copyOf(consents);
    }

    public record AccountProfile(
        UUID id,
        String email,
        String displayName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {
    }

    public record Consent(
        String privacyPolicyVersion,
        String termsVersion,
        String source,
        Instant acceptedAt
    ) {
    }

    public record Sessions(
        int active,
        int revoked,
        int expired
    ) {
    }

    public record Resources(
        int ownedSites,
        int ownedPages,
        int ownedComments,
        int authoredComments,
        int moderationActions
    ) {
    }

    public record DeletionRequest(
        UUID id,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant confirmedAt,
        Instant cancelledAt
    ) {
    }
}
