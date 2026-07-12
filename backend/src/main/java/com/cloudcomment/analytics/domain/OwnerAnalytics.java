package com.cloudcomment.analytics.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OwnerAnalytics(
    String range,
    UUID siteId,
    String timeZone,
    AnalyticsBucket bucketGranularity,
    Instant from,
    Instant to,
    AnalyticsSummary summary,
    List<CommentTimePoint> commentsOverTime,
    AnalyticsComparison comparison,
    AnalyticsWorkload workload,
    List<ModerationStatusCount> moderationDistribution,
    List<ReactionTypeCount> reactionDistribution,
    List<TopPageAnalytics> topPages,
    List<ActiveCommenter> activeCommenters
) {

    public OwnerAnalytics {
        commentsOverTime = List.copyOf(commentsOverTime);
        moderationDistribution = List.copyOf(moderationDistribution);
        reactionDistribution = List.copyOf(reactionDistribution);
        topPages = List.copyOf(topPages);
        activeCommenters = List.copyOf(activeCommenters);
    }
}
