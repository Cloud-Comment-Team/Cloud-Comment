package com.cloudcomment.privacy.application;

import com.cloudcomment.privacy.domain.PrivacyEventType;
import com.cloudcomment.privacy.persistence.PrivacyAuditRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyAuditServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");

    @Test
    void recordRedactsSensitiveMetadataKeys() {
        CapturingAuditRepository repository = new CapturingAuditRepository();
        PrivacyAuditService service = new PrivacyAuditService(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        UUID userId = UUID.randomUUID();

        service.record(userId, PrivacyEventType.ACCOUNT_DELETION_REQUESTED, Map.of(
            "requestId", "safe-request-id",
            "confirmationToken", "raw-token",
            "password", "secret",
            "sessionToken", "raw-session"
        ));

        assertThat(repository.userId).isEqualTo(userId);
        assertThat(repository.eventType).isEqualTo(PrivacyEventType.ACCOUNT_DELETION_REQUESTED);
        assertThat(repository.metadata)
            .containsEntry("requestId", "safe-request-id")
            .containsEntry("confirmationToken", "[redacted]")
            .containsEntry("password", "[redacted]")
            .containsEntry("sessionToken", "[redacted]");
        assertThat(repository.createdAt).isEqualTo(NOW);
    }

    private static class CapturingAuditRepository implements PrivacyAuditRepository {

        private UUID userId;
        private PrivacyEventType eventType;
        private Map<String, Object> metadata;
        private Instant createdAt;

        @Override
        public void record(UUID userId, PrivacyEventType eventType, Map<String, Object> metadata, Instant createdAt) {
            this.userId = userId;
            this.eventType = eventType;
            this.metadata = metadata;
            this.createdAt = createdAt;
        }
    }
}
