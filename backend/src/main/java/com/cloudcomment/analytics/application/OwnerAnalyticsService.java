package com.cloudcomment.analytics.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.analytics.domain.AnalyticsRange;
import com.cloudcomment.analytics.domain.CommentTimePoint;
import com.cloudcomment.analytics.domain.ModerationStatusCount;
import com.cloudcomment.analytics.domain.OwnerAnalytics;
import com.cloudcomment.analytics.persistence.OwnerAnalyticsRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerAnalyticsService {

    private static final int TOP_PAGES_LIMIT = 5;
    private static final int ACTIVE_COMMENTERS_LIMIT = 5;
    private static final List<String> STATUS_ORDER = List.of("APPROVED", "PENDING", "SPAM", "REJECTED", "HIDDEN");

    private final OwnerAnalyticsRepository ownerAnalyticsRepository;
    private final ResourceOwnershipService resourceOwnershipService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OwnerAnalytics getOwnerAnalytics(AuthenticatedUser currentUser, String rangeValue, UUID siteId) {
        AnalyticsRange range = AnalyticsRange.fromApiValue(rangeValue);
        if (siteId != null) {
            resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        }

        Instant now = clock.instant();
        Instant from = range.from(now);

        List<CommentTimePoint> commentsOverTime = ownerAnalyticsRepository.findCommentsOverTime(
            currentUser.id(),
            siteId,
            from,
            now,
            range.bucket()
        );
        List<ModerationStatusCount> moderationFunnel = ownerAnalyticsRepository.findModerationFunnel(
            currentUser.id(),
            siteId,
            from,
            now
        );

        return new OwnerAnalytics(
            range.apiValue(),
            siteId,
            from,
            now,
            ownerAnalyticsRepository.summarize(currentUser.id(), siteId, from, now),
            fillDailyPointsIfNeeded(range, from, commentsOverTime),
            fillStatusCounts(moderationFunnel),
            ownerAnalyticsRepository.findReactionDistribution(currentUser.id(), siteId, from, now),
            ownerAnalyticsRepository.findTopPages(currentUser.id(), siteId, from, now, TOP_PAGES_LIMIT),
            ownerAnalyticsRepository.findActiveCommenters(currentUser.id(), siteId, from, now, ACTIVE_COMMENTERS_LIMIT)
        );
    }

    private List<CommentTimePoint> fillDailyPointsIfNeeded(
        AnalyticsRange range,
        Instant from,
        List<CommentTimePoint> source
    ) {
        if (from == null) {
            return source;
        }

        Map<LocalDate, CommentTimePoint> sourceByDate = source.stream()
            .collect(Collectors.toMap(CommentTimePoint::bucket, Function.identity()));
        LocalDate start = LocalDate.ofInstant(from, ZoneOffset.UTC);
        LocalDate today = LocalDate.now(clock);
        long days = switch (range) {
            case DAYS_7 -> 7;
            case DAYS_30 -> 30;
            case DAYS_90 -> 90;
            case ALL -> 0;
        };

        return start.datesUntil(today.plusDays(1))
            .limit(days)
            .map(date -> sourceByDate.getOrDefault(date, new CommentTimePoint(date, 0, 0, 0, 0)))
            .toList();
    }

    private List<ModerationStatusCount> fillStatusCounts(List<ModerationStatusCount> source) {
        Map<String, Long> countsByStatus = new LinkedHashMap<>();
        for (String status : STATUS_ORDER) {
            countsByStatus.put(status, 0L);
        }
        for (ModerationStatusCount count : source) {
            countsByStatus.put(count.status(), count.count());
        }
        return countsByStatus.entrySet().stream()
            .map(entry -> new ModerationStatusCount(entry.getKey(), entry.getValue()))
            .toList();
    }
}
