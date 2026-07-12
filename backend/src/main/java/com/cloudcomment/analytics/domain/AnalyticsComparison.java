package com.cloudcomment.analytics.domain;

import java.time.Instant;

public record AnalyticsComparison(
    Instant previousFrom,
    Instant previousTo,
    MetricComparison comments,
    MetricComparison reactions,
    MetricComparison automaticDecisions,
    MetricComparison manualDecisions,
    MetricComparison undoActions
) {

    public static AnalyticsComparison between(
        Instant previousFrom,
        Instant previousTo,
        PeriodActivity current,
        PeriodActivity previous
    ) {
        return new AnalyticsComparison(
            previousFrom,
            previousTo,
            MetricComparison.between(current.comments(), previous.comments()),
            MetricComparison.between(current.reactions(), previous.reactions()),
            MetricComparison.between(current.automaticDecisions(), previous.automaticDecisions()),
            MetricComparison.between(current.manualDecisions(), previous.manualDecisions()),
            MetricComparison.between(current.undoActions(), previous.undoActions())
        );
    }
}
