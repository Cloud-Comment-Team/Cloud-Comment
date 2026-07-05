package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.TopPageAnalytics;

import java.time.Instant;
import java.util.UUID;

record TopPageAnalyticsResponse(
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

    static TopPageAnalyticsResponse from(TopPageAnalytics page) {
        return new TopPageAnalyticsResponse(
            page.pageId(),
            page.siteId(),
            page.siteName(),
            page.pageUrl(),
            page.comments(),
            page.replies(),
            page.reactions(),
            page.approved(),
            page.pending(),
            page.spam(),
            page.lastCommentAt()
        );
    }
}
