package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.MetricComparison;

record MetricComparisonResponse(
    long current,
    long previous,
    long absoluteChange,
    Double percentageChange
) {

    static MetricComparisonResponse from(MetricComparison comparison) {
        return new MetricComparisonResponse(
            comparison.current(),
            comparison.previous(),
            comparison.absoluteChange(),
            comparison.percentageChange()
        );
    }
}
