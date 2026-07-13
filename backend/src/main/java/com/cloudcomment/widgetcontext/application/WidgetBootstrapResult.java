package com.cloudcomment.widgetcontext.application;

import java.time.Instant;

public record WidgetBootstrapResult(
    String ticket,
    Instant expiresAt,
    String canonicalPageUrl,
    String publicKeyFingerprint
) {
}
