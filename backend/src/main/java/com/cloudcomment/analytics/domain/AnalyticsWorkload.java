package com.cloudcomment.analytics.domain;

import java.time.Instant;

public record AnalyticsWorkload(
    long requiringDecision,
    Instant oldestPendingAt,
    long automaticDecisions,
    long manualDecisions,
    long undoActions
) {
}
