package com.cloudcomment.account.application;

import com.cloudcomment.account.persistence.AccountLifecycleRepository;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.privacy.application.PrivacyAuditService;
import com.cloudcomment.privacy.domain.PrivacyEventType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountDeletionService {

    private final UserAccountRepository userAccountRepository;
    private final AccountLifecycleRepository accountLifecycleRepository;
    private final PrivacyAuditService privacyAuditService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountDeletionService(
        UserAccountRepository userAccountRepository,
        AccountLifecycleRepository accountLifecycleRepository,
        PrivacyAuditService privacyAuditService,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.accountLifecycleRepository = accountLifecycleRepository;
        this.privacyAuditService = privacyAuditService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public AccountDeletionReport deleteAccount(UUID userId) {
        Instant now = clock.instant();
        RelatedPersonalDataAnonymization anonymization =
            accountLifecycleRepository.anonymizeRelatedPersonalData(userId, now);
        String anonymizedEmail = "deleted-" + userId + "@deleted.invalid";
        String unusablePasswordHash = passwordEncoder.encode("deleted-" + userId);
        userAccountRepository.markAccountDeleted(userId, anonymizedEmail, unusablePasswordHash, now);
        int sessionsRevoked = userAccountRepository.revokeAllSessions(userId, now);
        AccountDeletionReport report = new AccountDeletionReport(
            anonymization.ownedSitesDeleted(),
            anonymization.authoredCommentsAnonymized(),
            anonymization.moderationActionsAnonymized(),
            sessionsRevoked
        );
        privacyAuditService.record(userId, PrivacyEventType.ACCOUNT_DELETED, Map.of(
            "ownedSitesDeleted", report.ownedSitesDeleted(),
            "authoredCommentsAnonymized", report.authoredCommentsAnonymized(),
            "moderationActionsAnonymized", report.moderationActionsAnonymized(),
            "sessionsRevoked", report.sessionsRevoked()
        ));
        return report;
    }
}
