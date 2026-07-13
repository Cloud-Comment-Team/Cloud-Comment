package com.cloudcomment.auth.application;

import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.auth.domain.SessionAudience;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-06-24T12:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void loginNormalizesEmailVerifiesPasswordAndCreatesSession() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-06-23T12:00:00Z");
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.of(new UserCredentials(
            userId,
            "user@example.com",
            passwordEncoder.encode("strong-password"),
            true,
            Set.of("COMMENTER"),
            timestamp,
            timestamp
        )));
        LoginService service = new LoginService(repository, passwordEncoder, FIXED_CLOCK, new SessionTokenHasher());

        LoginResult result = service.login(" User@Example.COM ", "strong-password", SessionAudience.WIDGET);

        assertThat(repository.lookupEmail).isEqualTo("user@example.com");
        assertThat(result.token()).isNotBlank();
        assertThat(result.token()).isNotEqualTo(repository.createdTokenHash);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresAt()).isEqualTo("2026-07-01T12:00:00Z");
        assertThat(result.user().id()).isEqualTo(userId);
        assertThat(result.user().email()).isEqualTo("user@example.com");
        assertThat(result.user().roles()).containsExactly("COMMENTER");
        assertThat(repository.sessionUserId).isEqualTo(userId);
        assertThat(repository.createdTokenHash)
            .matches("[0-9a-f]{64}");
        assertThat(repository.sessionExpiresAt).isEqualTo("2026-07-01T12:00:00Z");
        assertThat(repository.createdAudience).isEqualTo(SessionAudience.WIDGET);
    }

    @Test
    void loginRejectsUnknownEmail() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.empty());
        LoginService service = new LoginService(repository, passwordEncoder, FIXED_CLOCK, new SessionTokenHasher());

        assertThatThrownBy(() -> service.login(
            "missing@example.com", "strong-password", SessionAudience.WIDGET
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Invalid email or password")
            .extracting("code")
            .hasToString("INVALID_CREDENTIALS");
        assertThat(repository.createdTokenHash).isNull();
    }

    @Test
    void loginRejectsWrongPassword() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.of(new UserCredentials(
            UUID.randomUUID(),
            "user@example.com",
            passwordEncoder.encode("strong-password"),
            true,
            Set.of("COMMENTER"),
            Instant.parse("2026-06-23T12:00:00Z"),
            Instant.parse("2026-06-23T12:00:00Z")
        )));
        LoginService service = new LoginService(repository, passwordEncoder, FIXED_CLOCK, new SessionTokenHasher());

        assertThatThrownBy(() -> service.login(
            "user@example.com", "wrong-password", SessionAudience.WIDGET
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Invalid email or password");
        assertThat(repository.createdTokenHash).isNull();
    }

    @Test
    void loginRejectsDisabledUser() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.of(new UserCredentials(
            UUID.randomUUID(),
            "user@example.com",
            passwordEncoder.encode("strong-password"),
            false,
            Set.of("COMMENTER"),
            Instant.parse("2026-06-23T12:00:00Z"),
            Instant.parse("2026-06-23T12:00:00Z")
        )));
        LoginService service = new LoginService(repository, passwordEncoder, FIXED_CLOCK, new SessionTokenHasher());

        assertThatThrownBy(() -> service.login(
            "user@example.com", "strong-password", SessionAudience.WIDGET
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Invalid email or password");
        assertThat(repository.createdTokenHash).isNull();
    }

    @Test
    void adminReloginCreatesFreshSessionBeforeRevokingPreviousCookieSession() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-06-23T12:00:00Z");
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(Optional.of(new UserCredentials(
            userId,
            "user@example.com",
            passwordEncoder.encode("strong-password"),
            true,
            Set.of("OWNER"),
            timestamp,
            timestamp
        )));
        SessionTokenHasher tokenHasher = new SessionTokenHasher();
        LoginService service = new LoginService(repository, passwordEncoder, FIXED_CLOCK, tokenHasher);

        LoginResult result = service.loginReplacing(
            "user@example.com",
            "strong-password",
            SessionAudience.ADMIN,
            "previous-cookie-token"
        );

        assertThat(result.token()).isNotEqualTo("previous-cookie-token");
        assertThat(repository.operations).containsExactly("create", "revoke");
        assertThat(repository.revokedTokenHash).isEqualTo(tokenHasher.hash("previous-cookie-token"));
        assertThat(repository.createdAudience).isEqualTo(SessionAudience.ADMIN);
        assertThat(repository.revokedAudience).isEqualTo(SessionAudience.ADMIN);
        assertThat(repository.revokedAt).isEqualTo(FIXED_CLOCK.instant());
    }

    private static class CapturingUserAccountRepository implements UserAccountRepository {

        private final Optional<UserCredentials> credentials;

        private String lookupEmail;
        private UUID sessionUserId;
        private String createdTokenHash;
        private Instant sessionExpiresAt;
        private String revokedTokenHash;
        private Instant revokedAt;
        private SessionAudience createdAudience;
        private SessionAudience revokedAudience;
        private final List<String> operations = new ArrayList<>();

        private CapturingUserAccountRepository(Optional<UserCredentials> credentials) {
            this.credentials = credentials;
        }

        @Override
        public boolean existsByEmail(String email) {
            return credentials.isPresent() && credentials.orElseThrow().email().equals(email);
        }

        @Override
        public Optional<UserCredentials> findCredentialsByEmail(String email) {
            lookupEmail = email;
            return credentials.filter(user -> user.email().equals(email));
        }

        @Override
        public Optional<AuthenticatedUser> findUserByActiveSessionTokenHash(
            String tokenHash,
            SessionAudience audience,
            Instant now
        ) {
            return Optional.empty();
        }

        @Override
        public RegisteredUser create(String email, String passwordHash, Set<String> roles) {
            throw new UnsupportedOperationException("login tests do not create users");
        }

        @Override
        public void createSession(UUID userId, String tokenHash, SessionAudience audience, Instant expiresAt) {
            operations.add("create");
            createdAudience = audience;
            sessionUserId = userId;
            createdTokenHash = tokenHash;
            sessionExpiresAt = expiresAt;
        }

        @Override
        public com.cloudcomment.auth.persistence.SessionRevocationResult revokeSession(
            String tokenHash,
            SessionAudience audience,
            Instant revokedAt
        ) {
            operations.add("revoke");
            revokedAudience = audience;
            this.revokedTokenHash = tokenHash;
            this.revokedAt = revokedAt;
            return com.cloudcomment.auth.persistence.SessionRevocationResult.REVOKED;
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
