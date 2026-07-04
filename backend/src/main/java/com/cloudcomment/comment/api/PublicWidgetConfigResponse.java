package com.cloudcomment.comment.api;

import com.cloudcomment.comment.application.PublicWidgetConfig;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.api.WidgetStyleResponse;

import java.util.UUID;

record PublicWidgetConfigResponse(
    UUID siteId,
    ModerationMode moderationMode,
    WidgetStyleResponse style
) {

    static PublicWidgetConfigResponse from(PublicWidgetConfig config) {
        return new PublicWidgetConfigResponse(
            config.siteId(),
            config.moderationMode(),
            WidgetStyleResponse.from(config.widgetStyle())
        );
    }
}
