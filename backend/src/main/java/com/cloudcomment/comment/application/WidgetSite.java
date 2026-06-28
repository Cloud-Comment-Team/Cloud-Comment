package com.cloudcomment.comment.application;

import com.cloudcomment.site.domain.ModerationMode;

import java.util.UUID;

public record WidgetSite(
    UUID id,
    ModerationMode moderationMode
) {
}
