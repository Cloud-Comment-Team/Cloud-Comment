package com.cloudcomment.site.domain;

import java.time.Instant;
import java.util.UUID;

public record SiteWidgetHealth(
    UUID siteId,
    String lastSuccessfulOrigin,
    Instant lastSuccessfulAt,
    String lastRejectedOrigin,
    Instant lastRejectedAt
) {
}
