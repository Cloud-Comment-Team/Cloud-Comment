package com.cloudcomment.site.api;

import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SiteResponse(
    UUID id,
    UUID ownerId,
    String name,
    String domain,
    String publicKey,
    ModerationMode moderationMode,
    boolean isActive,
    WidgetStyleResponse widgetStyle,
    AutoModerationSettingsResponse autoModeration,
    List<String> allowedOrigins,
    Instant createdAt,
    Instant updatedAt
) {

    public SiteResponse {
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    static SiteResponse from(Site site) {
        return new SiteResponse(
            site.id(),
            site.ownerId(),
            site.name(),
            site.domain(),
            site.publicKey(),
            site.moderationMode(),
            site.active(),
            WidgetStyleResponse.from(site.widgetStyle()),
            AutoModerationSettingsResponse.from(site.autoModeration()),
            site.allowedOrigins(),
            site.createdAt(),
            site.updatedAt()
        );
    }
}
