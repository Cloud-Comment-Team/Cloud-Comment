package com.cloudcomment.privacy.persistence;

import com.cloudcomment.privacy.domain.PrivacyEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface PrivacyAuditRepository {

    void record(UUID userId, PrivacyEventType eventType, Map<String, Object> metadata, Instant createdAt);
}
