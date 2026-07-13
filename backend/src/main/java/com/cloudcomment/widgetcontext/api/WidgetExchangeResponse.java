package com.cloudcomment.widgetcontext.api;

import com.cloudcomment.widgetcontext.application.WidgetFrameContextResult;

import java.time.Instant;

public record WidgetExchangeResponse(
    String contextToken,
    Instant expiresAt
) {

    static WidgetExchangeResponse from(WidgetFrameContextResult result) {
        return new WidgetExchangeResponse(result.contextToken(), result.expiresAt());
    }
}
