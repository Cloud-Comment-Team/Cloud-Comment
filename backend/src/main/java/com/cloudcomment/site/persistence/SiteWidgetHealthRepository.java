package com.cloudcomment.site.persistence;

import com.cloudcomment.site.domain.SiteWidgetHealth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SiteWidgetHealthRepository {

    void recordSuccessfulOrigin(UUID siteId, String origin, Instant occurredAt);

    void recordRejectedOrigin(UUID siteId, String origin, Instant occurredAt);

    Optional<SiteWidgetHealth> findBySiteId(UUID siteId);

    boolean hasComments(UUID siteId);

    int clearRejectedBefore(Instant threshold);
}
