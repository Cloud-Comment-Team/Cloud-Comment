package com.cloudcomment.service;

import com.cloudcomment.api.error.ApiException;
import com.cloudcomment.persistence.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationServiceTests {

    @Test
    void registerNormalizesEmailHashesPasswordAndAssignsCommenterRole() {
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(false);
        RegistrationService service = new RegistrationService(repository, new BCryptPasswordEncoder());

        RegisteredUser user = service.register(" User@Example.COM ", "strong-password");

        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.roles()).containsExactly("COMMENTER");
        assertThat(repository.createdEmail).isEqualTo("user@example.com");
        assertThat(repository.createdRoles).containsExactly("COMMENTER");
        assertThat(repository.createdPasswordHash).isNotEqualTo("strong-password");
        assertThat(new BCryptPasswordEncoder().matches("strong-password", repository.createdPasswordHash)).isTrue();
    }

    @Test
    void registerRejectsExistingEmail() {
        RegistrationService service = new RegistrationService(
            new CapturingUserAccountRepository(true),
            new BCryptPasswordEncoder()
        );

        assertThatThrownBy(() -> service.register("used@example.com", "strong-password"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Email is already used")
            .extracting("code")
            .hasToString("EMAIL_ALREADY_USED");
    }

    @Test
    void registerMapsDuplicateKeyRaceToEmailAlreadyUsed() {
        CapturingUserAccountRepository repository = new CapturingUserAccountRepository(false);
        repository.throwDuplicateOnCreate = true;
        RegistrationService service = new RegistrationService(repository, new BCryptPasswordEncoder());

        assertThatThrownBy(() -> service.register("used@example.com", "strong-password"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Email is already used");
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
        public com.cloudcomment.persistence.SessionRevocationResult revokeSession(String tokenHash, Instant revokedAt) {
            throw new UnsupportedOperationException("registration tests do not revoke sessions");
        }
    }
}
