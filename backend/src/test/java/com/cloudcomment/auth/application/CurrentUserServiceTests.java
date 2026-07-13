package com.cloudcomment.auth.application;

import com.cloudcomment.auth.domain.SessionAudience;
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

        AuthenticatedUser result = service.getCurrentUser("plain-session-token", SessionAudience.ADMIN);

        assertThat(result).isEqualTo(user);
        assertThat(repository.currentTokenHash).matches("[0-9a-f]{64}");
        assertThat(repository.currentTokenHash).isNotEqualTo("plain-session-token");
        assertThat(repository.currentAudience).isEqualTo(SessionAudience.ADMIN);
        assertThat(repository.currentNow).isEqualTo("2026-06-24T12:00:00Z");
    }

    @Test
    void currentUserRejectsMissingRevokedOrExpiredSession() {
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.empty());
        CurrentUserService service = new CurrentUserService(repository, new SessionTokenHasher(), FIXED_CLOCK);

        assertThatThrownBy(() -> service.getCurrentUser("expired-session-token", SessionAudience.ADMIN))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Invalid or expired session")
            .extracting("code")
            .hasToString("INVALID_SESSION");
    }

    @Test
    void currentUserRejectsLegacyAudienceBeforeRepositoryLookup() {
        Instant timestamp = Instant.parse("2026-06-23T12:00:00Z");
        AuthenticatedUser user = new AuthenticatedUser(
            UUID.randomUUID(),
            "user@example.com",
            Set.of("COMMENTER"),
            timestamp,
            timestamp
        );
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.of(user));
        CurrentUserService service = new CurrentUserService(repository, new SessionTokenHasher(), FIXED_CLOCK);

        assertThatThrownBy(() -> service.getCurrentUser("legacy-token", SessionAudience.LEGACY))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Invalid or expired session");

        assertThat(repository.currentAudience).isNull();
    }

    @Test
    void widgetUserLookupUsesOnlySiteAndOriginScope() {
        UUID siteId = UUID.randomUUID();
        String origin = "https://customer.example";
        AuthenticatedUser user = new AuthenticatedUser(
            UUID.randomUUID(),
            "visitor@example.com",
            Set.of("COMMENTER"),
            FIXED_CLOCK.instant(),
            FIXED_CLOCK.instant()
        );
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.of(user));
        CurrentUserService service = new CurrentUserService(repository, new SessionTokenHasher(), FIXED_CLOCK);

        assertThat(service.getWidgetCurrentUser("widget-token", siteId, origin)).isEqualTo(user);
        assertThat(repository.currentAudience).isEqualTo(SessionAudience.WIDGET);
        assertThat(repository.currentSiteId).isEqualTo(siteId);
        assertThat(repository.currentOrigin).isEqualTo(origin);
    }

    private static class CapturingUserAccountRepository implements UserAccountRepository {

        private final Optional<AuthenticatedUser> currentUser;

        private String currentTokenHash;
        private SessionAudience currentAudience;
        private Instant currentNow;
        private UUID currentSiteId;
        private String currentOrigin;

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
        public Optional<AuthenticatedUser> findUserByActiveSessionTokenHash(
            String tokenHash,
            SessionAudience audience,
            Instant now
        ) {
            currentTokenHash = tokenHash;
            currentAudience = audience;
            currentNow = now;
            return currentUser;
        }

        @Override
        public Optional<AuthenticatedUser> findUserByActiveSessionTokenHash(
            String tokenHash,
            SessionAudience audience,
            UUID siteId,
            String origin,
            Instant now
        ) {
            currentTokenHash = tokenHash;
            currentAudience = audience;
            currentSiteId = siteId;
            currentOrigin = origin;
            currentNow = now;
            return currentUser;
        }

        @Override
        public RegisteredUser create(String email, String passwordHash, Set<String> roles) {
            throw new UnsupportedOperationException("current user tests do not create users");
        }

        @Override
        public void createSession(UUID userId, String tokenHash, SessionAudience audience, Instant expiresAt) {
            throw new UnsupportedOperationException("current user tests do not create sessions");
        }

        @Override
        public SessionRevocationResult revokeSession(
            String tokenHash,
            SessionAudience audience,
            Instant revokedAt
        ) {
            throw new UnsupportedOperationException("current user tests do not revoke sessions");
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
