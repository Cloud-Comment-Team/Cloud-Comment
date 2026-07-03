package com.cloudcomment.privacy.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public class NoopPrivacyAuditService extends PrivacyAuditService {

    public NoopPrivacyAuditService() {
        super(
            (userId, eventType, metadata, createdAt) -> {
            },
            Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC)
        );
    }
}
