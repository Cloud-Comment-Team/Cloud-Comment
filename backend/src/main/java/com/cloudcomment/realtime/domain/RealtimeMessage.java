package com.cloudcomment.realtime.domain;

import java.time.Instant;

public record RealtimeMessage(
    String type,
    Instant sentAt,
    Object payload
) {
}
