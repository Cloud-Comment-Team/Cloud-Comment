package com.cloudcomment.analytics.domain;

import java.time.Instant;
import java.util.UUID;

public record ActiveCommenter(
    UUID userId,
    String email,
    String displayName,
    long comments,
    long approved,
    long pending,
    long rejectedOrSpam,
    Instant lastActivityAt
) {
}
