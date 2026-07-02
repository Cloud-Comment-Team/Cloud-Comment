package com.cloudcomment.account.application;

import java.time.Instant;
import java.util.UUID;

public record AccountDeletionRequestView(
    UUID id,
    UUID userId,
    String status,
    Instant createdAt,
    Instant expiresAt
) {

    static AccountDeletionRequestView from(com.cloudcomment.account.domain.AccountDeletionRequest request) {
        return new AccountDeletionRequestView(
            request.id(),
            request.userId(),
            "PENDING",
            request.createdAt(),
            request.expiresAt()
        );
    }
}
