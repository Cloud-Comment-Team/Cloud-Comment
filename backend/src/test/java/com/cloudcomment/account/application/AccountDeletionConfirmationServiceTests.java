package com.cloudcomment.account.application;

import com.cloudcomment.account.domain.AccountDeletionRequest;
import com.cloudcomment.account.persistence.AccountDeletionRequestRepository;
import com.cloudcomment.auth.application.SessionTokenHasher;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountDeletionConfirmationServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void confirmDeletesAccountOnHappyPath() {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String token = "confirmation-token";
        CapturingDeletionRequestRepository repository = new CapturingDeletionRequestRepository(
            Optional.of(pendingRequest(requestId, userId, new SessionTokenHasher().hash(token)))
        );
        CapturingAccountDeletionService deletionService = new CapturingAccountDeletionService();
        CapturingUserAccountRepository userRepository = new CapturingUserAccountRepository(true);

        AccountDeletionConfirmationService service = new AccountDeletionConfirmationService(
            repository,
            deletionService,
            userRepository,
            new SessionTokenHasher(),
            CLOCK
        );

        service.confirm(token);

        assertThat(repository.confirmedRequestId).isEqualTo(requestId);
        assertThat(deletionService.deletedUserId).isEqualTo(userId);
    }

    @Test
    void confirmRejectsExpiredToken() {
        UUID userId = UUID.randomUUID();
        String token = "expired-token";
        AccountDeletionRequest expired = new AccountDeletionRequest(
            UUID.randomUUID(),
            userId,
            new SessionTokenHasher().hash(token),
            NOW.minusSeconds(7200),
            NOW.minusSeconds(3600),
            null,
            null
        );

        AccountDeletionConfirmationService service = service(
            new CapturingDeletionRequestRepository(Optional.of(expired)),
            new CapturingAccountDeletionService(),
            new CapturingUserAccountRepository(true)
        );

        assertThatThrownBy(() -> service.confirm(token))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Account deletion token has expired")
            .extracting("code")
            .hasToString("BUSINESS_ERROR");
    }

    @Test
    void confirmRejectsReusedToken() {
        UUID userId = UUID.randomUUID();
        String token = "used-token";
        AccountDeletionRequest reused = new AccountDeletionRequest(
            UUID.randomUUID(),
            userId,
            new SessionTokenHasher().hash(token),
            NOW.minusSeconds(3600),
            NOW.plusSeconds(3600),
            NOW.minusSeconds(1800),
            null
        );

        AccountDeletionConfirmationService service = service(
            new CapturingDeletionRequestRepository(Optional.of(reused)),
            new CapturingAccountDeletionService(),
            new CapturingUserAccountRepository(true)
        );

        assertThatThrownBy(() -> service.confirm(token))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Account deletion token has already been used")
            .extracting("code")
            .hasToString("BUSINESS_ERROR");
    }

    @Test
    void confirmRejectsUnknownToken() {
        AccountDeletionConfirmationService service = service(
            new CapturingDeletionRequestRepository(Optional.empty()),
            new CapturingAccountDeletionService(),
            new CapturingUserAccountRepository(true)
        );

        assertThatThrownBy(() -> service.confirm("missing-token"))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    private AccountDeletionConfirmationService service(
        CapturingDeletionRequestRepository repository,
        CapturingAccountDeletionService deletionService,
        CapturingUserAccountRepository userRepository
    ) {
        return new AccountDeletionConfirmationService(
            repository,
            deletionService,
            userRepository,
            new SessionTokenHasher(),
            CLOCK
        );
    }

    private AccountDeletionRequest pendingRequest(UUID requestId, UUID userId, String tokenHash) {
        return new AccountDeletionRequest(
            requestId,
            userId,
            tokenHash,
            NOW.minusSeconds(60),
            NOW.plusSeconds(3600),
            null,
            null
        );
    }

    private static class CapturingDeletionRequestRepository implements AccountDeletionRequestRepository {

        private final Optional<AccountDeletionRequest> request;
        private UUID confirmedRequestId;

        private CapturingDeletionRequestRepository(Optional<AccountDeletionRequest> request) {
            this.request = request;
        }

        @Override
        public Optional<AccountDeletionRequest> findActiveByUserId(UUID userId, Instant now) {
            return Optional.empty();
        }

        @Override
        public Optional<AccountDeletionRequest> findByTokenHash(String tokenHash) {
            return request.filter(value -> value.tokenHash().equals(tokenHash));
        }

        @Override
        public AccountDeletionRequest create(UUID userId, String tokenHash, Instant expiresAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AccountDeletionRequest rotateToken(UUID requestId, String tokenHash, Instant expiresAt, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markConfirmed(UUID requestId, Instant confirmedAt) {
            this.confirmedRequestId = requestId;
        }

        @Override
        public boolean tryMarkConfirmed(UUID requestId, Instant confirmedAt) {
            if (request.isEmpty() || !request.orElseThrow().id().equals(requestId) || request.orElseThrow().confirmedAt() != null) {
                return false;
            }
            this.confirmedRequestId = requestId;
            return true;
        }

        @Override
        public void cancelPendingForUser(UUID userId, Instant cancelledAt) {
        }
    }

    private static class CapturingAccountDeletionService extends AccountDeletionService {

        private UUID deletedUserId;

        private CapturingAccountDeletionService() {
            super(null, null, CLOCK);
        }

        @Override
        public void deleteAccount(UUID userId) {
            this.deletedUserId = userId;
        }
    }

    private static class CapturingUserAccountRepository implements UserAccountRepository {

        private final boolean active;

        private CapturingUserAccountRepository(boolean active) {
            this.active = active;
        }

        @Override
        public boolean existsByEmail(String email) {
            return false;
        }

        @Override
        public Optional<com.cloudcomment.auth.application.UserCredentials> findCredentialsByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<com.cloudcomment.auth.application.AuthenticatedUser> findUserByActiveSessionTokenHash(
            String tokenHash,
            Instant now
        ) {
            return Optional.empty();
        }

        @Override
        public com.cloudcomment.auth.application.RegisteredUser create(
            String email,
            String passwordHash,
            java.util.Set<String> roles
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void createSession(UUID userId, String tokenHash, Instant expiresAt) {
        }

        @Override
        public com.cloudcomment.auth.persistence.SessionRevocationResult revokeSession(String tokenHash, Instant revokedAt) {
            return com.cloudcomment.auth.persistence.SessionRevocationResult.NOT_FOUND_OR_EXPIRED;
        }

        @Override
        public int revokeAllSessions(UUID userId, Instant revokedAt) {
            return 0;
        }

        @Override
        public boolean isActiveAccount(UUID userId) {
            return active;
        }

        @Override
        public void markAccountDeleted(
            UUID userId,
            String anonymizedEmail,
            String unusablePasswordHash,
            Instant deletedAt
        ) {
        }
    }
}
