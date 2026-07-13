package com.cloudcomment.widgetcontext.persistence;

import java.time.Instant;
import java.util.UUID;

public record StoredWidgetBootstrapTicket(
    UUID siteId,
    String origin,
    String canonicalPageUrl,
    String pageUrlHash,
    String publicKeyFingerprint,
    byte[] publicKeySpki,
    Instant expiresAt
) {

    public StoredWidgetBootstrapTicket {
        publicKeySpki = publicKeySpki.clone();
    }

    @Override
    public byte[] publicKeySpki() {
        return publicKeySpki.clone();
    }
}
