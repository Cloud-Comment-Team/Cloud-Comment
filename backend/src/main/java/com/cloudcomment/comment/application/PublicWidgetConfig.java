package com.cloudcomment.comment.application;

import com.cloudcomment.site.domain.ModerationMode;

import java.util.UUID;

public record PublicWidgetConfig(
    UUID siteId,
    ModerationMode moderationMode
) {
}
