package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.AnalyticsComparison;

import java.time.Instant;

record AnalyticsComparisonResponse(
    Instant previousFrom,
    Instant previousTo,
    MetricComparisonResponse comments,
    MetricComparisonResponse reactions,
    MetricComparisonResponse automaticDecisions,
    MetricComparisonResponse manualDecisions,
    MetricComparisonResponse undoActions
) {

    static AnalyticsComparisonResponse from(AnalyticsComparison comparison) {
        if (comparison == null) {
            return null;
        }
        return new AnalyticsComparisonResponse(
            comparison.previousFrom(),
            comparison.previousTo(),
            MetricComparisonResponse.from(comparison.comments()),
            MetricComparisonResponse.from(comparison.reactions()),
            MetricComparisonResponse.from(comparison.automaticDecisions()),
            MetricComparisonResponse.from(comparison.manualDecisions()),
            MetricComparisonResponse.from(comparison.undoActions())
        );
    }
}
