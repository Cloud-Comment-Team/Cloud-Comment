package com.cloudcomment.auth.application;

import com.cloudcomment.privacy.application.ConsentService;
import com.cloudcomment.privacy.application.ConsentTestSupport;
import com.cloudcomment.privacy.application.NoopPrivacyAuditService;
import com.cloudcomment.privacy.application.PrivacyProperties;
import com.cloudcomment.privacy.application.RegistrationConsent;
import com.cloudcomment.privacy.domain.ConsentSource;
import com.cloudcomment.privacy.persistence.UserConsentRepository;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-23T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void registerNormalizesEmailHashesPasswordAssignsRoleAndRecordsConsent() {
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(false);
        CapturingUserConsentRepository consentRepository = new CapturingUserConsentRepository();
        RegistrationService service = service(repository, consentRepository);

        RegisteredUser user = service.register(
            " User@Example.COM ",
            "strong-password",
            ConsentTestSupport.validConsent(),
            ConsentSource.ADMIN
        );

        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.roles()).containsExactly("COMMENTER");
        assertThat(repository.createdEmail).isEqualTo("user@example.com");
        assertThat(consentRepository.savedUserId).isEqualTo(user.id());
        assertThat(consentRepository.savedSource).isEqualTo(ConsentSource.ADMIN);
        assertThat(consentRepository.savedPrivacyVersion).isEqualTo(ConsentTestSupport.PRIVACY_POLICY_VERSION);
    }

    @Test
    void registerRejectsOutdatedConsentVersion() {
        RegistrationService service = service(new CapturingUserAccountRepository(false), new CapturingUserConsentRepository());

        assertThatThrownBy(() -> service.register(
            "user@example.com",
            "strong-password",
            new RegistrationConsent(true, true, "2020-01-01", ConsentTestSupport.TERMS_VERSION),
            ConsentSource.ADMIN
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Privacy policy version is outdated")
            .extracting("code")
            .hasToString("VALIDATION_FAILED");
    }

    @Test
    void registerRejectsExistingEmail() {
        RegistrationService service = service(
            new CapturingUserAccountRepository(true),
            new CapturingUserConsentRepository()
        );

        assertThatThrownBy(() -> service.register(
            "used@example.com",
            "strong-password",
            ConsentTestSupport.validConsent(),
            ConsentSource.ADMIN
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Email is already used")
            .extracting("code")
            .hasToString("EMAIL_ALREADY_USED");
    }

    @Test
    void registerMapsDuplicateKeyRaceToEmailAlreadyUsed() {
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(false);
        repository.throwDuplicateOnCreate = true;
        RegistrationService service = service(repository, new CapturingUserConsentRepository());

        assertThatThrownBy(() -> service.register(
            "used@example.com",
            "strong-password",
            ConsentTestSupport.validConsent(),
            ConsentSource.ADMIN
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Email is already used");
    }

    private RegistrationService service(
        CapturingUserAccountRepository repository,
        CapturingUserConsentRepository consentRepository
    ) {
        ConsentService consentService = new ConsentService(
            consentRepository,
            new PrivacyProperties(null, null, null, null, null, null, 0, 0, null),
            new NoopPrivacyAuditService(),
            CLOCK
        );
        return new RegistrationService(repository, new BCryptPasswordEncoder(), consentService);
    }

    private static class CapturingUserConsentRepository implements UserConsentRepository {

        private UUID savedUserId;
        private String savedPrivacyVersion;
        private String savedTermsVersion;
        private ConsentSource savedSource;

        @Override
        public void save(
            UUID userId,
            String privacyPolicyVersion,
            String termsVersion,
            ConsentSource source,
            Instant acceptedAt
        ) {
            this.savedUserId = userId;
            this.savedPrivacyVersion = privacyPolicyVersion;
            this.savedTermsVersion = termsVersion;
            this.savedSource = source;
        }
    }

    private static class CapturingUserAccountRepository implements UserAccountRepository {

        private final boolean existsByEmail;

        private String createdEmail;
        private String createdPasswordHash;
        private Set<String> createdRoles;
        private boolean throwDuplicateOnCreate;

        private CapturingUserAccountRepository(boolean existsByEmail) {
            this.existsByEmail = existsByEmail;
        }

        @Override
        public boolean existsByEmail(String email) {
            return existsByEmail;
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
            if (throwDuplicateOnCreate) {
                throw new DuplicateKeyException("duplicate email");
            }
            createdEmail = email;
            createdPasswordHash = passwordHash;
            createdRoles = Set.copyOf(roles);
            Instant timestamp = Instant.parse("2026-06-23T12:00:00Z");
            return new RegisteredUser(UUID.randomUUID(), email, roles, timestamp, timestamp);
        }

        @Override
        public void createSession(UUID userId, String tokenHash, Instant expiresAt) {
            throw new UnsupportedOperationException("registration tests do not create sessions");
        }

        @Override
        public com.cloudcomment.auth.persistence.SessionRevocationResult revokeSession(String tokenHash, Instant revokedAt) {
            throw new UnsupportedOperationException("registration tests do not revoke sessions");
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
