package com.cloudcomment.auth.application;

import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.persistence.SessionRevocationResult;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogoutServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-06-24T12:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void logoutHashesTokenAndRevokesSession() {
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(SessionRevocationResult.REVOKED);
        LogoutService service = new LogoutService(repository, new SessionTokenHasher(), FIXED_CLOCK);

        assertThatCode(() -> service.logout("plain-session-token"))
            .doesNotThrowAnyException();

        assertThat(repository.revokedTokenHash).matches("[0-9a-f]{64}");
        assertThat(repository.revokedTokenHash).isNotEqualTo("plain-session-token");
        assertThat(repository.revokedAt).isEqualTo("2026-06-24T12:00:00Z");
    }

    @Test
    void logoutTreatsAlreadyRevokedSessionAsSuccess() {
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(
            SessionRevocationResult.ALREADY_REVOKED
        );
        LogoutService service = new LogoutService(repository, new SessionTokenHasher(), FIXED_CLOCK);

        assertThatCode(() -> service.logout("plain-session-token"))
            .doesNotThrowAnyException();
    }

    @Test
    void logoutRejectsMissingOrExpiredSession() {
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(
            SessionRevocationResult.NOT_FOUND_OR_EXPIRED
        );
        LogoutService service = new LogoutService(repository, new SessionTokenHasher(), FIXED_CLOCK);

        assertThatThrownBy(() -> service.logout("expired-session-token"))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Invalid or expired session")
            .extracting("code")
            .hasToString("INVALID_SESSION");
    }

    private static class CapturingUserAccountRepository implements UserAccountRepository {

        private final SessionRevocationResult revocationResult;

        private String revokedTokenHash;
        private Instant revokedAt;

        private CapturingUserAccountRepository(SessionRevocationResult revocationResult) {
            this.revocationResult = revocationResult;
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
            return Optional.empty();
        }

        @Override
        public RegisteredUser create(String email, String passwordHash, Set<String> roles) {
            throw new UnsupportedOperationException("logout tests do not create users");
        }

        @Override
        public void createSession(UUID userId, String tokenHash, Instant expiresAt) {
            throw new UnsupportedOperationException("logout tests do not create sessions");
        }

        @Override
        public SessionRevocationResult revokeSession(String tokenHash, Instant revokedAt) {
            this.revokedTokenHash = tokenHash;
            this.revokedAt = revokedAt;
            return revocationResult;
        }

        @Override
        public int revokeAllSessions(UUID userId, Instant revokedAt) {
            return 0;
        }

        @Override
        public boolean isActiveAccount(UUID userId) {
            return true;
        }

        @Override
        public void markAccountDeleted(UUID userId, String anonymizedEmail, String unusablePasswordHash, Instant deletedAt) {
        }
    }
}
