package com.cloudcomment.analytics.domain;

public record MetricComparison(
    long current,
    long previous,
    long absoluteChange,
    Double percentageChange
) {

    public static MetricComparison between(long current, long previous) {
        Double percentage = previous == 0
            ? null
            : Math.round(((double) current - previous) * 1000.0 / previous) / 10.0;
        return new MetricComparison(current, previous, current - previous, percentage);
    }
}
