package com.cloudcomment.account.application;

import com.cloudcomment.account.domain.AccountDeletionRequest;
import com.cloudcomment.account.persistence.AccountDeletionRequestRepository;
import com.cloudcomment.auth.application.SessionTokenHasher;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.privacy.application.PrivacyAuditService;
import com.cloudcomment.privacy.domain.PrivacyEventType;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
public class AccountDeletionConfirmationService {

    private final AccountDeletionRequestRepository deletionRequestRepository;
    private final AccountDeletionService accountDeletionService;
    private final UserAccountRepository userAccountRepository;
    private final SessionTokenHasher sessionTokenHasher;
    private final PrivacyAuditService privacyAuditService;
    private final Clock clock;

    public AccountDeletionConfirmationService(
        AccountDeletionRequestRepository deletionRequestRepository,
        AccountDeletionService accountDeletionService,
        UserAccountRepository userAccountRepository,
        SessionTokenHasher sessionTokenHasher,
        PrivacyAuditService privacyAuditService,
        Clock clock
    ) {
        this.deletionRequestRepository = deletionRequestRepository;
        this.accountDeletionService = accountDeletionService;
        this.userAccountRepository = userAccountRepository;
        this.sessionTokenHasher = sessionTokenHasher;
        this.privacyAuditService = privacyAuditService;
        this.clock = clock;
    }

    @Transactional
    public void confirm(String token) {
        String normalizedToken = normalizeToken(token);
        String tokenHash = sessionTokenHasher.hash(normalizedToken);
        Instant now = clock.instant();

        AccountDeletionRequest request = deletionRequestRepository.findByTokenHash(tokenHash)
            .orElseThrow(this::invalidToken);

        if (request.confirmedAt() != null) {
            throw tokenAlreadyUsed();
        }
        if (request.cancelledAt() != null || !request.expiresAt().isAfter(now)) {
            throw tokenExpired();
        }
        if (!userAccountRepository.isActiveAccount(request.userId())) {
            throw invalidToken();
        }

        if (!deletionRequestRepository.tryMarkConfirmed(request.id(), now)) {
            throw tokenAlreadyUsed();
        }
        privacyAuditService.record(request.userId(), PrivacyEventType.ACCOUNT_DELETION_CONFIRMED, Map.of(
            "requestId", request.id().toString()
        ));
        accountDeletionService.deleteAccount(request.userId());
    }

    private String normalizeToken(String token) {
        if (token == null) {
            throw invalidToken();
        }
        String normalized = token.trim();
        if (normalized.isEmpty()) {
            throw invalidToken();
        }
        return normalized;
    }

    private ApplicationException invalidToken() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }

    private ApplicationException tokenExpired() {
        return new ApplicationException(ApiErrorCode.BUSINESS_ERROR, "Account deletion token has expired");
    }

    private ApplicationException tokenAlreadyUsed() {
        return new ApplicationException(ApiErrorCode.BUSINESS_ERROR, "Account deletion token has already been used");
    }
}
