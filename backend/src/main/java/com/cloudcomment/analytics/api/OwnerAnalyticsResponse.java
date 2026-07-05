package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.OwnerAnalytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record OwnerAnalyticsResponse(
    String range,
    UUID siteId,
    Instant from,
    Instant to,
    AnalyticsSummaryResponse summary,
    List<CommentTimePointResponse> commentsOverTime,
    List<ModerationStatusCountResponse> moderationFunnel,
    List<ReactionTypeCountResponse> reactionDistribution,
    List<TopPageAnalyticsResponse> topPages,
    List<ActiveCommenterResponse> activeCommenters
) {

    OwnerAnalyticsResponse {
        commentsOverTime = List.copyOf(commentsOverTime);
        moderationFunnel = List.copyOf(moderationFunnel);
        reactionDistribution = List.copyOf(reactionDistribution);
        topPages = List.copyOf(topPages);
        activeCommenters = List.copyOf(activeCommenters);
    }

    static OwnerAnalyticsResponse from(OwnerAnalytics analytics) {
        return new OwnerAnalyticsResponse(
            analytics.range(),
            analytics.siteId(),
            analytics.from(),
            analytics.to(),
            AnalyticsSummaryResponse.from(analytics.summary()),
            analytics.commentsOverTime().stream().map(CommentTimePointResponse::from).toList(),
            analytics.moderationFunnel().stream().map(ModerationStatusCountResponse::from).toList(),
            analytics.reactionDistribution().stream().map(ReactionTypeCountResponse::from).toList(),
            analytics.topPages().stream().map(TopPageAnalyticsResponse::from).toList(),
            analytics.activeCommenters().stream().map(ActiveCommenterResponse::from).toList()
        );
    }
}
