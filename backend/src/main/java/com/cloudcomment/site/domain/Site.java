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
    AutoModerationSettings autoModeration,
    List<String> allowedOrigins,
    Instant createdAt,
    Instant updatedAt
) {

    public Site {
        widgetStyle = widgetStyle != null ? widgetStyle : WidgetStyle.defaultStyle();
        autoModeration = autoModeration != null ? autoModeration : AutoModerationSettings.defaultSettings();
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
        WidgetStyle widgetStyle,
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
            widgetStyle,
            AutoModerationSettings.defaultSettings(),
            allowedOrigins,
            createdAt,
            updatedAt
        );
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
            AutoModerationSettings.defaultSettings(),
            allowedOrigins,
            createdAt,
            updatedAt
        );
    }
}
