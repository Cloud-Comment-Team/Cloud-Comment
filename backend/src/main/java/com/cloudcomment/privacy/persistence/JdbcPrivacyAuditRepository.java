package com.cloudcomment.privacy.persistence;

import com.cloudcomment.privacy.domain.PrivacyEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcPrivacyAuditRepository implements PrivacyAuditRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void record(UUID userId, PrivacyEventType eventType, Map<String, Object> metadata, Instant createdAt) {
        jdbcTemplate.update(
            """
                insert into privacy_events (user_id, event_type, metadata, created_at)
                values (?, ?, ?::jsonb, ?)
                """,
            userId,
            eventType.name(),
            toJson(metadata),
            OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)
        );
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Privacy audit metadata is not serializable", exception);
        }
    }
}
