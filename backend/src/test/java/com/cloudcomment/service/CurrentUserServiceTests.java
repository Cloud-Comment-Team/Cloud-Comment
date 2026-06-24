package com.cloudcomment.service;

import com.cloudcomment.api.error.ApiException;
import com.cloudcomment.persistence.SessionRevocationResult;
import com.cloudcomment.persistence.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-06-24T12:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void currentUserHashesTokenAndReturnsActiveUser() {
        AuthenticatedUser user = new AuthenticatedUser(
            UUID.randomUUID(),
            "user@example.com",
            Set.of("COMMENTER"),
            Instant.parse("2026-06-23T12:00:00Z"),
            Instant.parse("2026-06-23T13:00:00Z")
        );
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.of(user));
        CurrentUserService service = new CurrentUserService(repository, new SessionTokenHasher(), FIXED_CLOCK);

        AuthenticatedUser result = service.getCurrentUser("plain-session-token");

        assertThat(result).isEqualTo(user);
        assertThat(repository.currentTokenHash).matches("[0-9a-f]{64}");
        assertThat(repository.currentTokenHash).isNotEqualTo("plain-session-token");
        assertThat(repository.currentNow).isEqualTo("2026-06-24T12:00:00Z");
    }

    @Test
    void currentUserRejectsMissingRevokedOrExpiredSession() {
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.empty());
        CurrentUserService service = new CurrentUserService(repository, new SessionTokenHasher(), FIXED_CLOCK);

        assertThatThrownBy(() -> service.getCurrentUser("expired-session-token"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Invalid or expired session")
            .extracting("code")
            .hasToString("INVALID_SESSION");
    }

    private static class CapturingUserAccountRepository implements UserAccountRepository {

        private final Optional<AuthenticatedUser> currentUser;

        private String currentTokenHash;
        private Instant currentNow;

        private CapturingUserAccountRepository(Optional<AuthenticatedUser> currentUser) {
            this.currentUser = currentUser;
        }

        @Override
        public boolean existsByEmail(String email) {
            return false;
        }

        @Override
        public Optional<UserCredentials> findCredentialsByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<AuthenticatedUser> findUserByActiveSessionTokenHash(String tokenHash, Instant now) {
            currentTokenHash = tokenHash;
            currentNow = now;
            return currentUser;
        }

        @Override
        public RegisteredUser create(String email, String passwordHash, Set<String> roles) {
            throw new UnsupportedOperationException("current user tests do not create users");
        }

        @Override
        public void createSession(UUID userId, String tokenHash, Instant expiresAt) {
            throw new UnsupportedOperationException("current user tests do not create sessions");
        }

        @Override
        public SessionRevocationResult revokeSession(String tokenHash, Instant revokedAt) {
            throw new UnsupportedOperationException("current user tests do not revoke sessions");
        }
    }
}
