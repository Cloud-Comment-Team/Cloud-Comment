package com.cloudcomment.analytics.domain;

import java.time.Instant;
import java.util.UUID;

public record TopPageAnalytics(
    UUID pageId,
    UUID siteId,
    String siteName,
    String pageUrl,
    long comments,
    long replies,
    long reactions,
    long approved,
    long pending,
    long spam,
    Instant lastCommentAt
) {
}
