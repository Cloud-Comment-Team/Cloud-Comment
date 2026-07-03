package com.cloudcomment.privacy.application;

import com.cloudcomment.account.persistence.AccountDeletionRequestRepository;
import com.cloudcomment.account.persistence.AccountLifecycleRepository;
import com.cloudcomment.privacy.domain.PrivacyEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrivacyRetentionService {

    private final AccountDeletionRequestRepository deletionRequestRepository;
    private final AccountLifecycleRepository accountLifecycleRepository;
    private final PrivacyProperties privacyProperties;
    private final PrivacyAuditService privacyAuditService;
    private final Clock clock;

    @Transactional
    public PrivacyRetentionReport cleanup() {
        Instant now = clock.instant();
        int expiredDeletionRequestsCancelled = deletionRequestRepository.cancelExpiredPending(now, now);
        int oldDeletionRequestsDeleted = deletionRequestRepository.deleteInactiveBefore(
            now.minus(privacyProperties.deletionRequestRetentionDays(), ChronoUnit.DAYS)
        );
        int inactiveSessionsDeleted = accountLifecycleRepository.deleteInactiveSessionsBefore(
            now.minus(privacyProperties.sessionRetentionDays(), ChronoUnit.DAYS)
        );

        PrivacyRetentionReport report = new PrivacyRetentionReport(
            expiredDeletionRequestsCancelled,
            oldDeletionRequestsDeleted,
            inactiveSessionsDeleted
        );
        privacyAuditService.record(null, PrivacyEventType.RETENTION_CLEANUP_COMPLETED, Map.of(
            "expiredDeletionRequestsCancelled", report.expiredDeletionRequestsCancelled(),
            "oldDeletionRequestsDeleted", report.oldDeletionRequestsDeleted(),
            "inactiveSessionsDeleted", report.inactiveSessionsDeleted()
        ));
        return report;
    }

    @Scheduled(cron = "${cloud-comment.privacy.retention-cleanup-cron:0 0 3 * * *}")
    void cleanupOnSchedule() {
        cleanup();
    }
}
