package com.cloudcomment.account.domain;

import java.time.Instant;
import java.util.UUID;

public record AccountDeletionRequest(
    UUID id,
    UUID userId,
    String tokenHash,
    Instant createdAt,
    Instant expiresAt,
    Instant confirmedAt,
    Instant cancelledAt
) {

    public boolean isPending(Instant now) {
        return confirmedAt == null && cancelledAt == null && expiresAt.isAfter(now);
    }
}
