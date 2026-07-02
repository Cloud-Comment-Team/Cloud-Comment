package com.cloudcomment.auth.persistence;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.RegisteredUser;
import com.cloudcomment.auth.application.UserCredentials;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserAccountRepository {

    boolean existsByEmail(String email);

    Optional<UserCredentials> findCredentialsByEmail(String email);

    Optional<AuthenticatedUser> findUserByActiveSessionTokenHash(String tokenHash, Instant now);

    RegisteredUser create(String email, String passwordHash, Set<String> roles);

    void createSession(UUID userId, String tokenHash, Instant expiresAt);

    SessionRevocationResult revokeSession(String tokenHash, Instant revokedAt);

    int revokeAllSessions(UUID userId, Instant revokedAt);

    boolean isActiveAccount(UUID userId);

    void markAccountDeleted(UUID userId, String anonymizedEmail, String unusablePasswordHash, Instant deletedAt);
}
