package com.cloudcomment.widgetcontext.persistence;

import java.time.Instant;
import java.util.UUID;

public record StoredWidgetFrameContext(
    UUID id,
    UUID siteId,
    String origin,
    String pageUrlHash,
    Instant expiresAt
) {
}
