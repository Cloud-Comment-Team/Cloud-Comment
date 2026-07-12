package com.cloudcomment.site.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.InstallationStatus;
import com.cloudcomment.site.domain.InstallationStatusReason;
import com.cloudcomment.site.domain.Site;
import com.cloudcomment.site.domain.SiteWidgetHealth;
import com.cloudcomment.site.persistence.SiteRepository;
import com.cloudcomment.site.persistence.SiteWidgetHealthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SiteInstallationHealthService {

    private static final Duration HEALTHY_WINDOW = Duration.ofHours(24);
    private static final Duration REJECTED_RETENTION = Duration.ofDays(30);

    private final SiteWidgetHealthRepository repository;
    private final SiteRepository siteRepository;
    private final ResourceOwnershipService resourceOwnershipService;
    private final Clock clock;

    @Transactional
    public void recordSuccessfulOrigin(UUID siteId, String normalizedOrigin) {
        repository.recordSuccessfulOrigin(siteId, normalizedOrigin, clock.instant());
    }

    @Transactional
    public void recordRejectedOrigin(UUID siteId, String normalizedOrigin) {
        repository.recordRejectedOrigin(siteId, normalizedOrigin, clock.instant());
    }

    @Transactional(readOnly = true)
    public SiteInstallationStatus getStatus(AuthenticatedUser currentUser, UUID siteId) {
        resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        Site site = siteRepository.findById(siteId).orElseThrow(this::notFound);
        SiteWidgetHealth health = repository.findBySiteId(siteId).orElse(null);
        StatusDecision decision = decide(site, health, clock.instant());
        return new SiteInstallationStatus(
            decision.status(),
            decision.reason(),
            true,
            !site.allowedOrigins().isEmpty(),
            health != null && health.lastSuccessfulAt() != null,
            repository.hasComments(siteId),
            health != null ? health.lastSuccessfulOrigin() : null,
            health != null ? health.lastSuccessfulAt() : null,
            health != null ? health.lastRejectedOrigin() : null,
            health != null ? health.lastRejectedAt() : null
        );
    }

    @Scheduled(cron = "${cloud-comment.installation-health.retention-cleanup-cron:0 45 3 * * *}")
    public void cleanupRejectedOrigins() {
        repository.clearRejectedBefore(clock.instant().minus(REJECTED_RETENTION));
    }

    private StatusDecision decide(Site site, SiteWidgetHealth health, Instant now) {
        if (!site.active()) {
            return new StatusDecision(InstallationStatus.REJECTED, InstallationStatusReason.SITE_INACTIVE);
        }
        if (health == null || (health.lastSuccessfulAt() == null && health.lastRejectedAt() == null)) {
            return new StatusDecision(InstallationStatus.NEVER_SEEN, InstallationStatusReason.WIDGET_NOT_SEEN);
        }
        boolean successfulOriginStillConfigured = health.lastSuccessfulOrigin() != null
            && site.allowedOrigins().contains(health.lastSuccessfulOrigin());
        boolean hasRecentConfiguredSuccess = successfulOriginStillConfigured
            && health.lastSuccessfulAt() != null
            && !health.lastSuccessfulAt().isBefore(now.minus(HEALTHY_WINDOW));
        if (hasRecentConfiguredSuccess) {
            return new StatusDecision(InstallationStatus.HEALTHY, InstallationStatusReason.RECENT_SUCCESS);
        }
        if (health.lastRejectedAt() != null
            && (health.lastSuccessfulAt() == null || health.lastRejectedAt().isAfter(health.lastSuccessfulAt()))) {
            return new StatusDecision(InstallationStatus.REJECTED, InstallationStatusReason.ORIGIN_REJECTED);
        }
        if (health.lastSuccessfulAt() != null && !successfulOriginStillConfigured) {
            return new StatusDecision(
                InstallationStatus.STALE,
                InstallationStatusReason.ORIGIN_CONFIGURATION_CHANGED
            );
        }
        return new StatusDecision(InstallationStatus.STALE, InstallationStatusReason.SUCCESS_STALE);
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }

    private record StatusDecision(InstallationStatus status, InstallationStatusReason reason) {
    }
}
