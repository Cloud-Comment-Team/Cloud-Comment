package com.cloudcomment.privacy.application;

import com.cloudcomment.privacy.domain.PrivacyEventType;
import com.cloudcomment.privacy.persistence.PrivacyAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrivacyAuditService {

    private static final String REDACTED = "[redacted]";

    private final PrivacyAuditRepository privacyAuditRepository;
    private final Clock clock;

    public void record(UUID userId, PrivacyEventType eventType) {
        record(userId, eventType, Map.of());
    }

    public void record(UUID userId, PrivacyEventType eventType, Map<String, ?> metadata) {
        privacyAuditRepository.record(userId, eventType, sanitize(metadata), clock.instant());
    }

    private Map<String, Object> sanitize(Map<String, ?> metadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (value != null) {
                sanitized.put(key, isSensitiveKey(key) ? REDACTED : value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private boolean isSensitiveKey(String key) {
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        return normalizedKey.contains("token")
            || normalizedKey.contains("password")
            || normalizedKey.contains("authorization")
            || normalizedKey.contains("secret");
    }
}
