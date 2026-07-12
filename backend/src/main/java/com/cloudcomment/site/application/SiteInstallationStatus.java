package com.cloudcomment.site.application;

import com.cloudcomment.site.domain.InstallationStatus;
import com.cloudcomment.site.domain.InstallationStatusReason;

import java.time.Instant;

public record SiteInstallationStatus(
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
}
