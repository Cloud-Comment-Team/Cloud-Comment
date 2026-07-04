package com.cloudcomment.site.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Site(
    UUID id,
    UUID ownerId,
    String name,
    String domain,
    String publicKey,
    ModerationMode moderationMode,
    boolean active,
    WidgetStyle widgetStyle,
    List<String> allowedOrigins,
    Instant createdAt,
    Instant updatedAt
) {

    public Site {
        widgetStyle = widgetStyle != null ? widgetStyle : WidgetStyle.defaultStyle();
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    public Site(
        UUID id,
        UUID ownerId,
        String name,
        String domain,
        String publicKey,
        ModerationMode moderationMode,
        boolean active,
        List<String> allowedOrigins,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            ownerId,
            name,
            domain,
            publicKey,
            moderationMode,
            active,
            WidgetStyle.defaultStyle(),
            allowedOrigins,
            createdAt,
            updatedAt
        );
    }
}
