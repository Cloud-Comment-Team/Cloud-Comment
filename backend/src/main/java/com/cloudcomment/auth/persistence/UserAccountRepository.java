package com.cloudcomment.auth.persistence;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.RegisteredUser;
import com.cloudcomment.auth.application.UserCredentials;
import com.cloudcomment.auth.domain.SessionAudience;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserAccountRepository {

    boolean existsByEmail(String email);

    Optional<UserCredentials> findCredentialsByEmail(String email);

    Optional<AuthenticatedUser> findUserByActiveSessionTokenHash(
        String tokenHash,
        SessionAudience audience,
        Instant now
    );

    default Optional<AuthenticatedUser> findUserByActiveSessionTokenHash(
        String tokenHash,
        SessionAudience audience,
        UUID siteId,
        String origin,
        Instant now
    ) {
        if (audience == SessionAudience.WIDGET) {
            throw new IllegalArgumentException("Widget session lookup requires an explicit scope");
        }
        return findUserByActiveSessionTokenHash(tokenHash, audience, now);
    }

    RegisteredUser create(String email, String passwordHash, Set<String> roles);

    void createSession(UUID userId, String tokenHash, SessionAudience audience, Instant expiresAt);

    default void createSession(
        UUID userId,
        String tokenHash,
        SessionAudience audience,
        UUID siteId,
        String origin,
        Instant expiresAt
    ) {
        if (audience == SessionAudience.WIDGET) {
            throw new IllegalArgumentException("Widget session creation requires an explicit scope");
        }
        createSession(userId, tokenHash, audience, expiresAt);
    }

    SessionRevocationResult revokeSession(String tokenHash, SessionAudience audience, Instant revokedAt);

    default SessionRevocationResult revokeSession(
        String tokenHash,
        SessionAudience audience,
        UUID siteId,
        String origin,
        Instant revokedAt
    ) {
        if (audience == SessionAudience.WIDGET) {
            throw new IllegalArgumentException("Widget session revocation requires an explicit scope");
        }
        return revokeSession(tokenHash, audience, revokedAt);
    }

    int revokeAllSessions(UUID userId, Instant revokedAt);

    boolean isActiveAccount(UUID userId);

    void markAccountDeleted(UUID userId, String anonymizedEmail, String unusablePasswordHash, Instant deletedAt);
}
