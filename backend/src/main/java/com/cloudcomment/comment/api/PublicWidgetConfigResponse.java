package com.cloudcomment.comment.api;

import com.cloudcomment.comment.application.PublicWidgetConfig;
import com.cloudcomment.site.domain.ModerationMode;

import java.util.UUID;

record PublicWidgetConfigResponse(
    UUID siteId,
    ModerationMode moderationMode
) {

    static PublicWidgetConfigResponse from(PublicWidgetConfig config) {
        return new PublicWidgetConfigResponse(config.siteId(), config.moderationMode());
    }
}
