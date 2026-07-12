package com.cloudcomment.site.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.site.domain.InstallationStatus;
import com.cloudcomment.site.domain.InstallationStatusReason;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;
import com.cloudcomment.site.domain.SiteWidgetHealth;
import com.cloudcomment.site.persistence.SiteRepository;
import com.cloudcomment.site.persistence.SiteWidgetHealthRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteInstallationHealthServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

    @Test
    void classifiesNeverSeenHealthyStaleRejectedAndInactiveSites() {
        Fixture fixture = new Fixture();
        fixture.assertStatus(null, true, InstallationStatus.NEVER_SEEN, InstallationStatusReason.WIDGET_NOT_SEEN);
        fixture.assertStatus(
            health(NOW.minusSeconds(60), null),
            true,
            InstallationStatus.HEALTHY,
            InstallationStatusReason.RECENT_SUCCESS
        );
        fixture.assertStatus(
            health(NOW.minusSeconds(86_401), null),
            true,
            InstallationStatus.STALE,
            InstallationStatusReason.SUCCESS_STALE
        );
        fixture.assertStatus(
            health(NOW.minusSeconds(60), NOW.minusSeconds(30)),
            true,
            InstallationStatus.HEALTHY,
            InstallationStatusReason.RECENT_SUCCESS
        );
        fixture.assertStatus(
            health(NOW.minusSeconds(86_401), NOW.minusSeconds(30)),
            true,
            InstallationStatus.REJECTED,
            InstallationStatusReason.ORIGIN_REJECTED
        );
        fixture.assertStatus(
            health(NOW.minusSeconds(60), null),
            false,
            InstallationStatus.REJECTED,
            InstallationStatusReason.SITE_INACTIVE
        );
    }

    @Test
    void recentSuccessStopsBeingHealthyAfterItsOriginIsRemoved() {
        Fixture fixture = new Fixture();
        when(fixture.repository.findBySiteId(fixture.siteId)).thenReturn(Optional.of(health(NOW, null)));
        when(fixture.siteRepository.findById(fixture.siteId))
            .thenReturn(Optional.of(fixture.site(true, List.of("https://other.example.com"))));

        SiteInstallationStatus result = fixture.service.getStatus(fixture.user, fixture.siteId);

        assertThat(result.status()).isEqualTo(InstallationStatus.STALE);
        assertThat(result.reason()).isEqualTo(InstallationStatusReason.ORIGIN_CONFIGURATION_CHANGED);
        assertThat(result.originConfigured()).isTrue();
    }

    @Test
    void statusChecksOwnershipAndBuildsActivationChecklist() {
        Fixture fixture = new Fixture();
        when(fixture.repository.hasComments(fixture.siteId)).thenReturn(true);
        when(fixture.repository.findBySiteId(fixture.siteId)).thenReturn(Optional.of(health(NOW, null)));
        when(fixture.siteRepository.findById(fixture.siteId)).thenReturn(Optional.of(fixture.site(true)));

        SiteInstallationStatus result = fixture.service.getStatus(fixture.user, fixture.siteId);

        verify(fixture.ownership).assertSiteOwnedBy(fixture.user, fixture.siteId);
        assertThat(result.siteCreated()).isTrue();
        assertThat(result.originConfigured()).isTrue();
        assertThat(result.widgetSeen()).isTrue();
        assertThat(result.firstCommentReceived()).isTrue();
    }

    @Test
    void recordsSafeEventsAndClearsRejectedDetailsAfterThirtyDays() {
        Fixture fixture = new Fixture();

        fixture.service.recordSuccessfulOrigin(fixture.siteId, "https://example.com");
        fixture.service.recordRejectedOrigin(fixture.siteId, "https://blocked.example.com");
        fixture.service.cleanupRejectedOrigins();

        verify(fixture.repository).recordSuccessfulOrigin(fixture.siteId, "https://example.com", NOW);
        verify(fixture.repository).recordRejectedOrigin(fixture.siteId, "https://blocked.example.com", NOW);
        verify(fixture.repository).clearRejectedBefore(NOW.minusSeconds(30L * 24 * 60 * 60));
    }

    private static SiteWidgetHealth health(Instant successAt, Instant rejectedAt) {
        return new SiteWidgetHealth(
            UUID.randomUUID(),
            successAt != null ? "https://example.com" : null,
            successAt,
            rejectedAt != null ? "https://blocked.example.com" : null,
            rejectedAt
        );
    }

    private static class Fixture {
        private final UUID siteId = UUID.randomUUID();
        private final AuthenticatedUser user = new AuthenticatedUser(
            UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), NOW, NOW
        );
        private final SiteWidgetHealthRepository repository = mock(SiteWidgetHealthRepository.class);
        private final SiteRepository siteRepository = mock(SiteRepository.class);
        private final ResourceOwnershipService ownership = mock(ResourceOwnershipService.class);
        private final SiteInstallationHealthService service = new SiteInstallationHealthService(
            repository,
            siteRepository,
            ownership,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        private void assertStatus(
            SiteWidgetHealth health,
            boolean active,
            InstallationStatus expectedStatus,
            InstallationStatusReason expectedReason
        ) {
            when(siteRepository.findById(siteId)).thenReturn(Optional.of(site(active)));
            when(repository.findBySiteId(siteId)).thenReturn(Optional.ofNullable(health));
            SiteInstallationStatus result = service.getStatus(user, siteId);
            assertThat(result.status()).isEqualTo(expectedStatus);
            assertThat(result.reason()).isEqualTo(expectedReason);
        }

        private Site site(boolean active) {
            return site(active, List.of("https://example.com"));
        }

        private Site site(boolean active, List<String> allowedOrigins) {
            return new Site(
                siteId,
                user.id(),
                "Example",
                "example.com",
                "public-key",
                ModerationMode.PRE_MODERATION,
                active,
                allowedOrigins,
                NOW,
                NOW
            );
        }
    }
}
