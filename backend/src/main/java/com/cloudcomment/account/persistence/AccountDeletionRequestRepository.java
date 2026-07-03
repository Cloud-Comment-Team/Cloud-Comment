package com.cloudcomment.account.persistence;

import com.cloudcomment.account.domain.AccountDeletionRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountDeletionRequestRepository {

    Optional<AccountDeletionRequest> findActiveByUserId(UUID userId, Instant now);

    Optional<AccountDeletionRequest> findByTokenHash(String tokenHash);

    AccountDeletionRequest create(UUID userId, String tokenHash, Instant expiresAt);

    AccountDeletionRequest rotateToken(UUID requestId, String tokenHash, Instant expiresAt, Instant now);

    void markConfirmed(UUID requestId, Instant confirmedAt);

    boolean tryMarkConfirmed(UUID requestId, Instant confirmedAt);

    void cancelPendingForUser(UUID userId, Instant cancelledAt);

    int cancelExpiredPending(Instant now, Instant cancelledAt);

    int deleteInactiveBefore(Instant cutoff);
}
