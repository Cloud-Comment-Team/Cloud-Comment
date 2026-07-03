package com.cloudcomment.account.application;

import com.cloudcomment.account.domain.AccountDeletionRequest;
import com.cloudcomment.account.persistence.AccountDeletionRequestRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.SessionTokenHasher;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.shared.mail.LoggingMailSender;
import com.cloudcomment.shared.mail.MailProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDeletionRequestServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createOrRefreshReusesActiveRequestInsteadOfCreatingAnotherRow() {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        CapturingDeletionRequestRepository repository = new CapturingDeletionRequestRepository(
            Optional.of(new AccountDeletionRequest(
                requestId,
                userId,
                "old-hash",
                NOW.minusSeconds(120),
                NOW.plusSeconds(1800),
                null,
                null
            ))
        );
        LoggingMailSender mailSender = new LoggingMailSender();
        AccountDeletionRequestService service = new AccountDeletionRequestService(
            repository,
            new AlwaysActiveUserRepository(),
            new SessionTokenHasher(),
            mailSender,
            new MailProperties("log", "noreply@test.local", "http://localhost/confirm", null),
            CLOCK
        );

        AccountDeletionRequestView view = service.createOrRefresh(currentUser(userId));

        assertThat(repository.createdCount).isZero();
        assertThat(repository.cancelledCount).isZero();
        assertThat(repository.rotatedRequestId).isEqualTo(requestId);
        assertThat(view.id()).isEqualTo(requestId);
        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(mailSender.lastSentMessage()).isNotNull();
        assertThat(mailSender.lastSentMessage().to()).isEqualTo("owner@example.com");
        assertThat(mailSender.lastSentMessage().subject()).isEqualTo("Подтверждение удаления аккаунта CloudComment");
        assertThat(mailSender.lastSentMessage().textBody())
            .contains("Чтобы подтвердить удаление, откройте ссылку:")
            .contains("Если вы не хотите переходить по ссылке")
            .contains("http://localhost/confirm");
    }

    @Test
    void createOrRefreshCreatesRequestWhenNoActiveRequestExists() {
        CapturingDeletionRequestRepository repository = new CapturingDeletionRequestRepository(Optional.empty());
        LoggingMailSender mailSender = new LoggingMailSender();
        UUID userId = UUID.randomUUID();
        AccountDeletionRequestService service = new AccountDeletionRequestService(
            repository,
            new AlwaysActiveUserRepository(),
            new SessionTokenHasher(),
            mailSender,
            new MailProperties("log", "noreply@test.local", "http://localhost/confirm", null),
            CLOCK
        );

        AccountDeletionRequestView view = service.createOrRefresh(currentUser(userId));

        assertThat(repository.createdCount).isOne();
        assertThat(repository.cancelledCount).isOne();
        assertThat(repository.cancelledUserId).isEqualTo(userId);
        assertThat(repository.cancelledAt).isEqualTo(NOW);
        assertThat(repository.createdUserId).isEqualTo(userId);
        assertThat(view.userId()).isEqualTo(userId);
        assertThat(mailSender.lastSentMessage()).isNotNull();
    }

    private AuthenticatedUser currentUser(UUID userId) {
        return new AuthenticatedUser(userId, "owner@example.com", Set.of("OWNER"), NOW, NOW);
    }

    private static class CapturingDeletionRequestRepository implements AccountDeletionRequestRepository {

        private final Optional<AccountDeletionRequest> activeRequest;
        private int createdCount;
        private UUID createdUserId;
        private UUID rotatedRequestId;
        private int cancelledCount;
        private UUID cancelledUserId;
        private Instant cancelledAt;

        private CapturingDeletionRequestRepository(Optional<AccountDeletionRequest> activeRequest) {
            this.activeRequest = activeRequest;
        }

        @Override
        public Optional<AccountDeletionRequest> findActiveByUserId(UUID userId, Instant now) {
            return activeRequest.filter(request -> request.userId().equals(userId));
        }

        @Override
        public Optional<AccountDeletionRequest> findByTokenHash(String tokenHash) {
            return Optional.empty();
        }

        @Override
        public AccountDeletionRequest create(UUID userId, String tokenHash, Instant expiresAt) {
            createdCount++;
            createdUserId = userId;
            return new AccountDeletionRequest(
                UUID.randomUUID(),
                userId,
                tokenHash,
                NOW,
                expiresAt,
                null,
                null
            );
        }

        @Override
        public AccountDeletionRequest rotateToken(UUID requestId, String tokenHash, Instant expiresAt, Instant now) {
            rotatedRequestId = requestId;
            return new AccountDeletionRequest(
                requestId,
                activeRequest.orElseThrow().userId(),
                tokenHash,
                now,
                expiresAt,
                null,
                null
            );
        }

        @Override
        public void markConfirmed(UUID requestId, Instant confirmedAt) {
        }

        @Override
        public boolean tryMarkConfirmed(UUID requestId, Instant confirmedAt) {
            return false;
        }

        @Override
        public void cancelPendingForUser(UUID userId, Instant cancelledAt) {
            cancelledCount++;
            cancelledUserId = userId;
            this.cancelledAt = cancelledAt;
        }
    }

    private static class AlwaysActiveUserRepository implements UserAccountRepository {

        @Override
        public boolean existsByEmail(String email) {
            return false;
        }

        @Override
        public Optional<com.cloudcomment.auth.application.UserCredentials> findCredentialsByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<AuthenticatedUser> findUserByActiveSessionTokenHash(String tokenHash, Instant now) {
            return Optional.empty();
        }

        @Override
        public com.cloudcomment.auth.application.RegisteredUser create(
            String email,
            String passwordHash,
            Set<String> roles
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
            return true;
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
