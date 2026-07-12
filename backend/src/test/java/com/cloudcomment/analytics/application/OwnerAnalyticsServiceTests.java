package com.cloudcomment.analytics.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.access.domain.OwnedResourceType;
import com.cloudcomment.access.persistence.ResourceOwnershipRepository;
import com.cloudcomment.analytics.domain.ActiveCommenter;
import com.cloudcomment.analytics.domain.AnalyticsBucket;
import com.cloudcomment.analytics.domain.AnalyticsSummary;
import com.cloudcomment.analytics.domain.AnalyticsWorkload;
import com.cloudcomment.analytics.domain.CommentTimePoint;
import com.cloudcomment.analytics.domain.MetricComparison;
import com.cloudcomment.analytics.domain.PeriodActivity;
import com.cloudcomment.analytics.domain.ReactionTypeCount;
import com.cloudcomment.analytics.domain.TopPageAnalytics;
import com.cloudcomment.analytics.persistence.OwnerAnalyticsRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerAnalyticsServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-05T15:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void ownerAnalyticsUsesUtcByDefaultContractAndFillsSevenDailyBuckets() {
        CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();
        OwnerAnalyticsService service = service(repository, true, CLOCK);
        AuthenticatedUser currentUser = currentUser();

        var analytics = service.getOwnerAnalytics(currentUser, "7d", null, "UTC");

        assertThat(repository.ownerId).isEqualTo(currentUser.id());
        assertThat(repository.siteId).isNull();
        assertThat(repository.from).isEqualTo(Instant.parse("2026-06-29T00:00:00Z"));
        assertThat(repository.to).isEqualTo(NOW);
        assertThat(repository.bucket).isEqualTo(AnalyticsBucket.DAY);
        assertThat(repository.timeZone).isEqualTo("UTC");
        assertThat(analytics.timeZone()).isEqualTo("UTC");
        assertThat(analytics.commentsOverTime()).hasSize(7);
        assertThat(analytics.commentsOverTime()).extracting(CommentTimePoint::bucket)
            .containsExactly(
                LocalDate.parse("2026-06-29"),
                LocalDate.parse("2026-06-30"),
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-02"),
                LocalDate.parse("2026-07-03"),
                LocalDate.parse("2026-07-04"),
                LocalDate.parse("2026-07-05")
            );
        assertThat(analytics.commentsOverTime().get(2).total()).isEqualTo(5);
        assertThat(analytics.comparison().previousFrom()).isEqualTo(Instant.parse("2026-06-22T00:00:00Z"));
        assertThat(analytics.comparison().previousTo()).isEqualTo(Instant.parse("2026-06-28T15:30:00Z"));
        assertThat(analytics.comparison().comments().percentageChange()).isNull();
        assertThat(analytics.moderationDistribution()).extracting(count -> count.status())
            .containsExactly("APPROVED", "PENDING", "SPAM", "REJECTED", "HIDDEN");
    }

    @Test
    void ninetyDaysUseIsoWeeksInRequestedTimeZone() {
        CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();
        OwnerAnalyticsService service = service(repository, true, CLOCK);

        var analytics = service.getOwnerAnalytics(currentUser(), "90d", null, "Europe/Moscow");

        assertThat(repository.from).isEqualTo(Instant.parse("2026-04-06T21:00:00Z"));
        assertThat(repository.bucket).isEqualTo(AnalyticsBucket.WEEK);
        assertThat(repository.timeZone).isEqualTo("Europe/Moscow");
        assertThat(analytics.bucketGranularity()).isEqualTo(AnalyticsBucket.WEEK);
        assertThat(analytics.commentsOverTime()).extracting(CommentTimePoint::bucket)
            .startsWith(LocalDate.parse("2026-04-06"))
            .endsWith(LocalDate.parse("2026-06-29"));
        assertThat(analytics.commentsOverTime()).hasSize(13);
    }

    @Test
    void localCalendarWindowsRemainDeterministicAcrossDst() {
        Instant dstNow = Instant.parse("2026-03-10T16:00:00Z");
        CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();
        OwnerAnalyticsService service = service(
            repository,
            true,
            Clock.fixed(dstNow, ZoneOffset.UTC)
        );

        var analytics = service.getOwnerAnalytics(currentUser(), "7d", null, "America/New_York");

        assertThat(analytics.from()).isEqualTo(Instant.parse("2026-03-04T05:00:00Z"));
        assertThat(analytics.comparison().previousFrom()).isEqualTo(Instant.parse("2026-02-25T05:00:00Z"));
        assertThat(analytics.comparison().previousTo()).isEqualTo(Instant.parse("2026-03-03T17:00:00Z"));
        assertThat(analytics.commentsOverTime()).extracting(CommentTimePoint::bucket)
            .containsExactly(
                LocalDate.parse("2026-03-04"),
                LocalDate.parse("2026-03-05"),
                LocalDate.parse("2026-03-06"),
                LocalDate.parse("2026-03-07"),
                LocalDate.parse("2026-03-08"),
                LocalDate.parse("2026-03-09"),
                LocalDate.parse("2026-03-10")
            );
    }

    @Test
    void allRangeUsesMonthlyBucketsFillsGapsAndHasNoComparison() {
        CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();
        repository.points = List.of(
            new CommentTimePoint(LocalDate.parse("2026-01-01"), 1, 1, 0, 0),
            new CommentTimePoint(LocalDate.parse("2026-03-01"), 2, 1, 1, 0)
        );
        OwnerAnalyticsService service = service(repository, true, CLOCK);

        var analytics = service.getOwnerAnalytics(currentUser(), "all", null, "UTC");

        assertThat(repository.from).isNull();
        assertThat(repository.bucket).isEqualTo(AnalyticsBucket.MONTH);
        assertThat(analytics.from()).isNull();
        assertThat(analytics.comparison()).isNull();
        assertThat(analytics.commentsOverTime()).hasSize(7);
        assertThat(analytics.commentsOverTime().get(1).bucket()).isEqualTo(LocalDate.parse("2026-02-01"));
        assertThat(analytics.commentsOverTime().get(1).total()).isZero();
    }

    @Test
    void allRangeWithNoCommentsKeepsEmptySeries() {
        CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();
        repository.points = List.of();

        var analytics = service(repository, true, CLOCK)
            .getOwnerAnalytics(currentUser(), "all", null, "UTC");

        assertThat(analytics.commentsOverTime()).isEmpty();
    }

    @Test
    void finiteSeriesRemainReadableForZeroOneAndTwoComments() {
        for (long total : List.of(0L, 1L, 2L)) {
            CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();
            repository.points = total == 0
                ? List.of()
                : List.of(new CommentTimePoint(LocalDate.parse("2026-07-05"), total, total, 0, 0));

            var analytics = service(repository, true, CLOCK)
                .getOwnerAnalytics(currentUser(), "7d", null, "UTC");

            assertThat(analytics.commentsOverTime()).hasSize(7);
            assertThat(analytics.commentsOverTime().getLast().total()).isEqualTo(total);
        }
    }

    @Test
    void siteAnalyticsChecksOwnershipAndMasksForeignSite() {
        UUID siteId = UUID.randomUUID();
        CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();

        service(repository, true, CLOCK).getOwnerAnalytics(currentUser(), "30d", siteId, "UTC");
        assertThat(repository.siteId).isEqualTo(siteId);

        assertThatThrownBy(() -> service(new CapturingOwnerAnalyticsRepository(), false, CLOCK)
            .getOwnerAnalytics(currentUser(), "30d", UUID.randomUUID(), "UTC"))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    @Test
    void unsupportedTimeZoneIsBadRequest() {
        assertThatThrownBy(() -> service(new CapturingOwnerAnalyticsRepository(), true, CLOCK)
            .getOwnerAnalytics(currentUser(), "30d", null, "Mars/Olympus"))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Unsupported analytics time zone")
            .extracting("code")
            .hasToString("BAD_REQUEST");
    }

    @Test
    void metricComparisonAvoidsDivisionByZeroAndHandlesDecrease() {
        assertThat(MetricComparison.between(2, 0).percentageChange()).isNull();
        assertThat(MetricComparison.between(5, 10))
            .extracting(MetricComparison::absoluteChange, MetricComparison::percentageChange)
            .containsExactly(-5L, -50.0);
    }

    private OwnerAnalyticsService service(
        CapturingOwnerAnalyticsRepository repository,
        boolean owned,
        Clock clock
    ) {
        return new OwnerAnalyticsService(
            repository,
            new ResourceOwnershipService(new FixedOwnershipRepository(owned)),
            clock
        );
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(
            UUID.randomUUID(),
            "owner@example.com",
            Set.of("OWNER"),
            Instant.parse("2026-06-23T12:00:00Z"),
            Instant.parse("2026-06-23T12:00:00Z")
        );
    }

    private static class CapturingOwnerAnalyticsRepository implements OwnerAnalyticsRepository {
        private List<CommentTimePoint> points = List.of(
            new CommentTimePoint(LocalDate.parse("2026-07-01"), 5, 3, 1, 1)
        );
        private UUID ownerId;
        private UUID siteId;
        private Instant from;
        private Instant to;
        private AnalyticsBucket bucket;
        private String timeZone;

        @Override
        public AnalyticsSummary summarize(UUID ownerId, UUID siteId, Instant from, Instant to) {
            capture(ownerId, siteId, from, to);
            return new AnalyticsSummary(2, 3, 5, 1, 4, 1, 3, 0, 0, 1);
        }

        @Override
        public List<CommentTimePoint> findCommentsOverTime(
            UUID ownerId,
            UUID siteId,
            Instant from,
            Instant to,
            AnalyticsBucket bucket,
            String timeZone
        ) {
            capture(ownerId, siteId, from, to);
            this.bucket = bucket;
            this.timeZone = timeZone;
            return points;
        }

        @Override
        public AnalyticsWorkload findWorkload(UUID ownerId, UUID siteId, Instant from, Instant to) {
            capture(ownerId, siteId, from, to);
            return new AnalyticsWorkload(2, Instant.parse("2026-07-01T10:00:00Z"), 3, 2, 1);
        }

        @Override
        public PeriodActivity findPeriodActivity(UUID ownerId, UUID siteId, Instant from, Instant to) {
            return new PeriodActivity(0, 0, 0, 0, 0);
        }

        @Override
        public List<ReactionTypeCount> findReactionDistribution(UUID ownerId, UUID siteId, Instant from, Instant to) {
            capture(ownerId, siteId, from, to);
            return List.of();
        }

        @Override
        public List<TopPageAnalytics> findTopPages(UUID ownerId, UUID siteId, Instant from, Instant to, int limit) {
            capture(ownerId, siteId, from, to);
            return List.of();
        }

        @Override
        public List<ActiveCommenter> findActiveCommenters(UUID ownerId, UUID siteId, Instant from, Instant to, int limit) {
            capture(ownerId, siteId, from, to);
            return List.of();
        }

        private void capture(UUID ownerId, UUID siteId, Instant from, Instant to) {
            this.ownerId = ownerId;
            this.siteId = siteId;
            this.from = from;
            this.to = to;
        }
    }

    private record FixedOwnershipRepository(boolean owned) implements ResourceOwnershipRepository {

        @Override
        public boolean isOwnedBy(UUID ownerId, OwnedResourceType type, UUID resourceId) {
            return owned;
        }
    }
}
