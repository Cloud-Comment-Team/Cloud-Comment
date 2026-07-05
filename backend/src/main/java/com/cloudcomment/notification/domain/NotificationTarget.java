package com.cloudcomment.notification.domain;

import java.util.UUID;

public record NotificationTarget(
    UUID ownerId,
    String siteName,
    String pageUrl
) {
}
