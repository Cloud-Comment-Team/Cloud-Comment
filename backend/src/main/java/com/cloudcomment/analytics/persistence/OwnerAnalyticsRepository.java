package com.cloudcomment.analytics.persistence;

import com.cloudcomment.analytics.domain.ActiveCommenter;
import com.cloudcomment.analytics.domain.AnalyticsBucket;
import com.cloudcomment.analytics.domain.AnalyticsSummary;
import com.cloudcomment.analytics.domain.CommentTimePoint;
import com.cloudcomment.analytics.domain.ModerationStatusCount;
import com.cloudcomment.analytics.domain.ReactionTypeCount;
import com.cloudcomment.analytics.domain.TopPageAnalytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OwnerAnalyticsRepository {

    AnalyticsSummary summarize(UUID ownerId, UUID siteId, Instant from, Instant to);

    List<CommentTimePoint> findCommentsOverTime(
        UUID ownerId,
        UUID siteId,
        Instant from,
        Instant to,
        AnalyticsBucket bucket
    );

    List<ModerationStatusCount> findModerationFunnel(UUID ownerId, UUID siteId, Instant from, Instant to);

    List<ReactionTypeCount> findReactionDistribution(UUID ownerId, UUID siteId, Instant from, Instant to);

    List<TopPageAnalytics> findTopPages(UUID ownerId, UUID siteId, Instant from, Instant to, int limit);

    List<ActiveCommenter> findActiveCommenters(UUID ownerId, UUID siteId, Instant from, Instant to, int limit);
}
