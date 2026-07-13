package com.cloudcomment.widgetcontext.application;

import java.time.Instant;
import java.util.UUID;

public record ResolvedWidgetContext(
    UUID id,
    UUID siteId,
    String origin,
    String pageUrlHash,
    Instant expiresAt
) {
}
