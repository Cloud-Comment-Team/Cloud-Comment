package com.cloudcomment.privacy.application;

import com.cloudcomment.account.application.RelatedPersonalDataAnonymization;
import com.cloudcomment.account.domain.AccountDeletionRequest;
import com.cloudcomment.account.persistence.AccountDeletionRequestRepository;
import com.cloudcomment.account.persistence.AccountLifecycleRepository;
import com.cloudcomment.privacy.domain.PrivacyEventType;
import com.cloudcomment.privacy.persistence.PrivacyAuditRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyRetentionServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void cleanupCancelsExpiredDeletionRequestsDeletesOldDataAndWritesAudit() {
        CapturingDeletionRequestRepository deletionRequests = new CapturingDeletionRequestRepository();
        CapturingLifecycleRepository lifecycleRepository = new CapturingLifecycleRepository();
        CapturingAuditRepository auditRepository = new CapturingAuditRepository();
        PrivacyRetentionService service = new PrivacyRetentionService(
            deletionRequests,
            lifecycleRepository,
            new PrivacyProperties(null, null, null, null, null, null, 14, 7, null),
            new PrivacyAuditService(auditRepository, CLOCK),
            CLOCK
        );

        PrivacyRetentionReport report = service.cleanup();

        assertThat(report.expiredDeletionRequestsCancelled()).isEqualTo(2);
        assertThat(report.oldDeletionRequestsDeleted()).isEqualTo(3);
        assertThat(report.inactiveSessionsDeleted()).isEqualTo(4);
        assertThat(deletionRequests.cancelExpiredNow).isEqualTo(NOW);
        assertThat(deletionRequests.deleteInactiveCutoff).isEqualTo(NOW.minusSeconds(7L * 24 * 60 * 60));
        assertThat(lifecycleRepository.sessionCutoff).isEqualTo(NOW.minusSeconds(14L * 24 * 60 * 60));
        assertThat(auditRepository.eventType).isEqualTo(PrivacyEventType.RETENTION_CLEANUP_COMPLETED);
        assertThat(auditRepository.metadata)
            .containsEntry("expiredDeletionRequestsCancelled", 2)
            .containsEntry("oldDeletionRequestsDeleted", 3)
            .containsEntry("inactiveSessionsDeleted", 4);
    }

    private static class CapturingDeletionRequestRepository implements AccountDeletionRequestRepository {

        private Instant cancelExpiredNow;
        private Instant deleteInactiveCutoff;

        @Override
        public Optional<AccountDeletionRequest> findActiveByUserId(UUID userId, Instant now) {
            return Optional.empty();
        }

        @Override
        public Optional<AccountDeletionRequest> findByTokenHash(String tokenHash) {
            return Optional.empty();
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
        }

        @Override
        public boolean tryMarkConfirmed(UUID requestId, Instant confirmedAt) {
            return false;
        }

        @Override
        public void cancelPendingForUser(UUID userId, Instant cancelledAt) {
        }

        @Override
        public int cancelExpiredPending(Instant now, Instant cancelledAt) {
            cancelExpiredNow = now;
            return 2;
        }

        @Override
        public int deleteInactiveBefore(Instant cutoff) {
            deleteInactiveCutoff = cutoff;
            return 3;
        }
    }

    private static class CapturingLifecycleRepository implements AccountLifecycleRepository {

        private Instant sessionCutoff;

        @Override
        public RelatedPersonalDataAnonymization anonymizeRelatedPersonalData(UUID userId, Instant anonymizedAt) {
            return new RelatedPersonalDataAnonymization(0, 0, 0, 0);
        }

        @Override
        public int deleteInactiveSessionsBefore(Instant cutoff) {
            sessionCutoff = cutoff;
            return 4;
        }
    }

    private static class CapturingAuditRepository implements PrivacyAuditRepository {

        private PrivacyEventType eventType;
        private Map<String, Object> metadata;

        @Override
        public void record(UUID userId, PrivacyEventType eventType, Map<String, Object> metadata, Instant createdAt) {
            this.eventType = eventType;
            this.metadata = metadata;
        }
    }
}
