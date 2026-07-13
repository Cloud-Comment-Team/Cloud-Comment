package com.cloudcomment.widgetcontext.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WidgetContextRepository {

    void createBootstrapTicket(
        String ticketHash,
        UUID siteId,
        String origin,
        String canonicalPageUrl,
        String pageUrlHash,
        String publicKeyFingerprint,
        byte[] publicKeySpki,
        Instant createdAt,
        Instant expiresAt
    );

    Optional<StoredWidgetBootstrapTicket> findActiveBootstrapTicket(
        String ticketHash,
        UUID siteId,
        Instant now
    );

    boolean consumeBootstrapTicket(
        String ticketHash,
        UUID siteId,
        Instant consumedAt
    );

    void deleteBootstrapTicket(String ticketHash, UUID siteId);

    void createFrameContext(
        String tokenHash,
        UUID siteId,
        String origin,
        String pageUrlHash,
        Instant createdAt,
        Instant expiresAt
    );

    Optional<StoredWidgetFrameContext> findActiveFrameContext(
        String tokenHash,
        UUID siteId,
        Instant now
    );

    int deleteExpiredBootstrapTickets(Instant cutoff);

    int deleteExpiredFrameContexts(Instant cutoff);
}
