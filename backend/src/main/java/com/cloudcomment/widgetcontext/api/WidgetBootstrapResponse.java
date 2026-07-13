package com.cloudcomment.widgetcontext.api;

import com.cloudcomment.widgetcontext.application.WidgetBootstrapResult;

import java.time.Instant;

public record WidgetBootstrapResponse(
    String ticket,
    Instant expiresAt,
    String canonicalPageUrl,
    String publicKeyFingerprint
) {

    static WidgetBootstrapResponse from(WidgetBootstrapResult result) {
        return new WidgetBootstrapResponse(
            result.ticket(),
            result.expiresAt(),
            result.canonicalPageUrl(),
            result.publicKeyFingerprint()
        );
    }
}
