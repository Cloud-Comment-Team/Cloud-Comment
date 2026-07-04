package com.cloudcomment.account.api;

import com.cloudcomment.account.application.AccountDeletionRequestView;

import java.time.Instant;
import java.util.UUID;

public record AccountDeletionRequestResponse(
    UUID id,
    UUID userId,
    String status,
    Instant createdAt,
    Instant expiresAt
) {

    public static AccountDeletionRequestResponse from(AccountDeletionRequestView view) {
        return new AccountDeletionRequestResponse(
            view.id(),
            view.userId(),
            view.status(),
            view.createdAt(),
            view.expiresAt()
        );
    }
}
