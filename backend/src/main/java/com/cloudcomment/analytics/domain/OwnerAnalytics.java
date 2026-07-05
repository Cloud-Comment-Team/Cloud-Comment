package com.cloudcomment.analytics.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OwnerAnalytics(
    String range,
    UUID siteId,
    Instant from,
    Instant to,
    AnalyticsSummary summary,
    List<CommentTimePoint> commentsOverTime,
    List<ModerationStatusCount> moderationFunnel,
    List<ReactionTypeCount> reactionDistribution,
    List<TopPageAnalytics> topPages,
    List<ActiveCommenter> activeCommenters
) {

    public OwnerAnalytics {
        commentsOverTime = List.copyOf(commentsOverTime);
        moderationFunnel = List.copyOf(moderationFunnel);
        reactionDistribution = List.copyOf(reactionDistribution);
        topPages = List.copyOf(topPages);
        activeCommenters = List.copyOf(activeCommenters);
    }
}
