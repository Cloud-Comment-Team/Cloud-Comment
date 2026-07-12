package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.AnalyticsWorkload;

import java.time.Instant;

record AnalyticsWorkloadResponse(
    long requiringDecision,
    Instant oldestPendingAt,
    long automaticDecisions,
    long manualDecisions,
    long undoActions
) {

    static AnalyticsWorkloadResponse from(AnalyticsWorkload workload) {
        return new AnalyticsWorkloadResponse(
            workload.requiringDecision(),
            workload.oldestPendingAt(),
            workload.automaticDecisions(),
            workload.manualDecisions(),
            workload.undoActions()
        );
    }
}
