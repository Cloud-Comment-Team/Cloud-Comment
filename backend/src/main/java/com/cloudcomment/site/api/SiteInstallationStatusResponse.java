package com.cloudcomment.site.api;

import com.cloudcomment.site.application.SiteInstallationStatus;
import com.cloudcomment.site.domain.InstallationStatus;
import com.cloudcomment.site.domain.InstallationStatusReason;

import java.time.Instant;

record SiteInstallationStatusResponse(
    InstallationStatus status,
    InstallationStatusReason reason,
    boolean siteCreated,
    boolean originConfigured,
    boolean widgetSeen,
    boolean firstCommentReceived,
    String lastSuccessfulOrigin,
    Instant lastSuccessfulAt,
    String lastRejectedOrigin,
    Instant lastRejectedAt
) {
    static SiteInstallationStatusResponse from(SiteInstallationStatus status) {
        return new SiteInstallationStatusResponse(
            status.status(),
            status.reason(),
            status.siteCreated(),
            status.originConfigured(),
            status.widgetSeen(),
            status.firstCommentReceived(),
            status.lastSuccessfulOrigin(),
            status.lastSuccessfulAt(),
            status.lastRejectedOrigin(),
            status.lastRejectedAt()
        );
    }
}
