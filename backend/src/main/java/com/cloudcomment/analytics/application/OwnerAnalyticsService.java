package com.cloudcomment.analytics.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.analytics.domain.AnalyticsBucket;
import com.cloudcomment.analytics.domain.AnalyticsComparison;
import com.cloudcomment.analytics.domain.AnalyticsRange;
import com.cloudcomment.analytics.domain.AnalyticsSummary;
import com.cloudcomment.analytics.domain.AnalyticsWorkload;
import com.cloudcomment.analytics.domain.CommentTimePoint;
import com.cloudcomment.analytics.domain.ModerationStatusCount;
import com.cloudcomment.analytics.domain.OwnerAnalytics;
import com.cloudcomment.analytics.domain.PeriodActivity;
import com.cloudcomment.analytics.persistence.OwnerAnalyticsRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerAnalyticsService {

    private static final int TOP_PAGES_LIMIT = 5;
    private static final int ACTIVE_COMMENTERS_LIMIT = 5;
    private static final Set<String> AVAILABLE_TIME_ZONES = ZoneId.getAvailableZoneIds();

    private final OwnerAnalyticsRepository ownerAnalyticsRepository;
    private final ResourceOwnershipService resourceOwnershipService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OwnerAnalytics getOwnerAnalytics(
        AuthenticatedUser currentUser,
        String rangeValue,
        UUID siteId,
        String timeZoneValue
    ) {
        AnalyticsRange range = AnalyticsRange.fromApiValue(rangeValue);
        ZoneId timeZone = parseTimeZone(timeZoneValue);
        if (siteId != null) {
            resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        }

        Instant now = clock.instant();
        Instant from = range.from(now, timeZone);
        AnalyticsSummary summary = ownerAnalyticsRepository.summarize(currentUser.id(), siteId, from, now);
        List<CommentTimePoint> commentsOverTime = ownerAnalyticsRepository.findCommentsOverTime(
            currentUser.id(),
            siteId,
            from,
            now,
            range.bucket(),
            timeZone.getId()
        );
        AnalyticsWorkload workload = ownerAnalyticsRepository.findWorkload(currentUser.id(), siteId, from, now);
        List<ModerationStatusCount> moderationDistribution = moderationDistribution(summary);

        return new OwnerAnalytics(
            range.apiValue(),
            siteId,
            timeZone.getId(),
            range.bucket(),
            from,
            now,
            summary,
            fillMissingPoints(range, timeZone, now, from, commentsOverTime),
            comparison(currentUser.id(), siteId, range, timeZone, now, summary, workload),
            workload,
            moderationDistribution,
            ownerAnalyticsRepository.findReactionDistribution(currentUser.id(), siteId, from, now),
            ownerAnalyticsRepository.findTopPages(currentUser.id(), siteId, from, now, TOP_PAGES_LIMIT),
            ownerAnalyticsRepository.findActiveCommenters(
                currentUser.id(), siteId, from, now, ACTIVE_COMMENTERS_LIMIT
            )
        );
    }

    private AnalyticsComparison comparison(
        UUID ownerId,
        UUID siteId,
        AnalyticsRange range,
        ZoneId timeZone,
        Instant now,
        AnalyticsSummary summary,
        AnalyticsWorkload workload
    ) {
        if (!range.supportsComparison()) {
            return null;
        }
        Instant previousFrom = range.previousFrom(now, timeZone);
        Instant previousTo = range.previousTo(now, timeZone);
        PeriodActivity previous = ownerAnalyticsRepository.findPeriodActivity(
            ownerId, siteId, previousFrom, previousTo
        );
        PeriodActivity current = new PeriodActivity(
            summary.comments(),
            summary.reactions(),
            workload.automaticDecisions(),
            workload.manualDecisions(),
            workload.undoActions()
        );
        return AnalyticsComparison.between(previousFrom, previousTo, current, previous);
    }

    private List<CommentTimePoint> fillMissingPoints(
        AnalyticsRange range,
        ZoneId timeZone,
        Instant now,
        Instant from,
        List<CommentTimePoint> source
    ) {
        if (range == AnalyticsRange.ALL && source.isEmpty()) {
            return List.of();
        }

        AnalyticsBucket bucket = range.bucket();
        Map<LocalDate, CommentTimePoint> sourceByDate = source.stream()
            .collect(Collectors.toMap(CommentTimePoint::bucket, Function.identity()));
        LocalDate start = range == AnalyticsRange.ALL
            ? bucket.start(source.getFirst().bucket())
            : bucket.start(LocalDate.ofInstant(from, timeZone));
        LocalDate end = bucket.start(LocalDate.ofInstant(now, timeZone));
        ArrayList<CommentTimePoint> points = new ArrayList<>();
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = bucket.next(cursor)) {
            points.add(sourceByDate.getOrDefault(cursor, new CommentTimePoint(cursor, 0, 0, 0, 0)));
        }
        return List.copyOf(points);
    }

    private List<ModerationStatusCount> moderationDistribution(AnalyticsSummary summary) {
        return List.of(
            new ModerationStatusCount("APPROVED", summary.approved()),
            new ModerationStatusCount("PENDING", summary.pending()),
            new ModerationStatusCount("SPAM", summary.spam()),
            new ModerationStatusCount("REJECTED", summary.rejected()),
            new ModerationStatusCount("HIDDEN", summary.hidden())
        );
    }

    private ZoneId parseTimeZone(String value) {
        if (value == null || value.isBlank() || !AVAILABLE_TIME_ZONES.contains(value)) {
            throw new ApplicationException(ApiErrorCode.BAD_REQUEST, "Unsupported analytics time zone");
        }
        return ZoneId.of(value);
    }
}
