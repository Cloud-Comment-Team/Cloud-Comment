package com.cloudcomment.persistence;

import com.cloudcomment.service.AuthenticatedUser;
import com.cloudcomment.service.RegisteredUser;
import com.cloudcomment.service.UserCredentials;

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
}
