package com.cloudcomment.widgetcontext.application;

import java.time.Instant;

public record WidgetFrameContextResult(
    String contextToken,
    Instant expiresAt
) {
}
