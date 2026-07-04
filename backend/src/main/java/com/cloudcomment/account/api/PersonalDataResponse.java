package com.cloudcomment.account.api;

import com.cloudcomment.account.application.PersonalDataSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PersonalDataResponse(
    AccountProfileResponse account,
    List<String> roles,
    List<ConsentResponse> consents,
    SessionsResponse sessions,
    ResourcesResponse resources,
    DeletionRequestResponse deletionRequest,
    Instant exportedAt
) {

    public static PersonalDataResponse from(PersonalDataSnapshot snapshot) {
        return new PersonalDataResponse(
            AccountProfileResponse.from(snapshot.account()),
            snapshot.roles(),
            snapshot.consents().stream().map(ConsentResponse::from).toList(),
            SessionsResponse.from(snapshot.sessions()),
            ResourcesResponse.from(snapshot.resources()),
            snapshot.deletionRequest() == null ? null : DeletionRequestResponse.from(snapshot.deletionRequest()),
            snapshot.exportedAt()
        );
    }

    record AccountProfileResponse(
        UUID id,
        String email,
        String displayName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {

        static AccountProfileResponse from(PersonalDataSnapshot.AccountProfile account) {
            return new AccountProfileResponse(
                account.id(),
                account.email(),
                account.displayName(),
                account.enabled(),
                account.createdAt(),
                account.updatedAt(),
                account.deletedAt()
            );
        }
    }

    record ConsentResponse(
        String privacyPolicyVersion,
        String termsVersion,
        String source,
        Instant acceptedAt
    ) {

        static ConsentResponse from(PersonalDataSnapshot.Consent consent) {
            return new ConsentResponse(
                consent.privacyPolicyVersion(),
                consent.termsVersion(),
                consent.source(),
                consent.acceptedAt()
            );
        }
    }

    record SessionsResponse(
        int active,
        int revoked,
        int expired
    ) {

        static SessionsResponse from(PersonalDataSnapshot.Sessions sessions) {
            return new SessionsResponse(sessions.active(), sessions.revoked(), sessions.expired());
        }
    }

    record ResourcesResponse(
        int ownedSites,
        int ownedPages,
        int ownedComments,
        int authoredComments,
        int moderationActions
    ) {

        static ResourcesResponse from(PersonalDataSnapshot.Resources resources) {
            return new ResourcesResponse(
                resources.ownedSites(),
                resources.ownedPages(),
                resources.ownedComments(),
                resources.authoredComments(),
                resources.moderationActions()
            );
        }
    }

    record DeletionRequestResponse(
        UUID id,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant confirmedAt,
        Instant cancelledAt
    ) {

        static DeletionRequestResponse from(PersonalDataSnapshot.DeletionRequest request) {
            return new DeletionRequestResponse(
                request.id(),
                request.status(),
                request.createdAt(),
                request.expiresAt(),
                request.confirmedAt(),
                request.cancelledAt()
            );
        }
    }
}
