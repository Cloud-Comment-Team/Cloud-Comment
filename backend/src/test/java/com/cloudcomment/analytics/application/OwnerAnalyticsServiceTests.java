package com.cloudcomment.analytics.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.access.domain.OwnedResourceType;
import com.cloudcomment.access.persistence.ResourceOwnershipRepository;
import com.cloudcomment.analytics.domain.ActiveCommenter;
import com.cloudcomment.analytics.domain.AnalyticsBucket;
import com.cloudcomment.analytics.domain.AnalyticsSummary;
import com.cloudcomment.analytics.domain.CommentTimePoint;
import com.cloudcomment.analytics.domain.ModerationStatusCount;
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
    void ownerAnalyticsCalculatesRangeAndFillsDailyBuckets() {
        CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();
        OwnerAnalyticsService service = service(repository, true);
        AuthenticatedUser currentUser = currentUser();

        var analytics = service.getOwnerAnalytics(currentUser, "7d", null);

        assertThat(repository.ownerId).isEqualTo(currentUser.id());
        assertThat(repository.siteId).isNull();
        assertThat(repository.from).isEqualTo(Instant.parse("2026-06-29T00:00:00Z"));
        assertThat(repository.to).isEqualTo(NOW);
        assertThat(repository.bucket).isEqualTo(AnalyticsBucket.DAY);
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
        assertThat(analytics.commentsOverTime().getFirst().total()).isZero();
        assertThat(analytics.moderationFunnel()).extracting(ModerationStatusCount::status)
            .containsExactly("APPROVED", "PENDING", "SPAM", "REJECTED", "HIDDEN");
    }

    @Test
    void allRangeUsesMonthlyBucketWithoutFromDate() {
        CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();
        OwnerAnalyticsService service = service(repository, true);

        var analytics = service.getOwnerAnalytics(currentUser(), "all", null);

        assertThat(repository.from).isNull();
        assertThat(repository.to).isEqualTo(NOW);
        assertThat(repository.bucket).isEqualTo(AnalyticsBucket.MONTH);
        assertThat(analytics.from()).isNull();
        assertThat(analytics.commentsOverTime()).containsExactlyElementsOf(repository.points);
    }

    @Test
    void siteAnalyticsChecksOwnership() {
        CapturingOwnerAnalyticsRepository repository = new CapturingOwnerAnalyticsRepository();
        OwnerAnalyticsService service = service(repository, true);
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();

        service.getOwnerAnalytics(currentUser, "30d", siteId);

        assertThat(repository.siteId).isEqualTo(siteId);
    }

    @Test
    void foreignSiteIsMaskedAsNotFound() {
        OwnerAnalyticsService service = service(new CapturingOwnerAnalyticsRepository(), false);

        assertThatThrownBy(() -> service.getOwnerAnalytics(currentUser(), "30d", UUID.randomUUID()))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    private OwnerAnalyticsService service(CapturingOwnerAnalyticsRepository repository, boolean owned) {
        return new OwnerAnalyticsService(
            repository,
            new ResourceOwnershipService(new FixedOwnershipRepository(owned)),
            CLOCK
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
        private final List<CommentTimePoint> points = List.of(
            new CommentTimePoint(LocalDate.parse("2026-07-01"), 5, 3, 1, 1)
        );
        private UUID ownerId;
        private UUID siteId;
        private Instant from;
        private Instant to;
        private AnalyticsBucket bucket;

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
            AnalyticsBucket bucket
        ) {
            capture(ownerId, siteId, from, to);
            this.bucket = bucket;
            return points;
        }

        @Override
        public List<ModerationStatusCount> findModerationFunnel(UUID ownerId, UUID siteId, Instant from, Instant to) {
            capture(ownerId, siteId, from, to);
            return List.of(
                new ModerationStatusCount("APPROVED", 3),
                new ModerationStatusCount("SPAM", 1)
            );
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
