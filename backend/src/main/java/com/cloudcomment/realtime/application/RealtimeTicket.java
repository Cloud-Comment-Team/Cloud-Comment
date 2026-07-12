package com.cloudcomment.realtime.application;

import java.time.Instant;

public record RealtimeTicket(
    String value,
    Instant expiresAt
) {
}
