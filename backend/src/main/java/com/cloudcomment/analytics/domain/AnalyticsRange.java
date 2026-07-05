package com.cloudcomment.analytics.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

public enum AnalyticsRange {
    DAYS_7("7d", 7, AnalyticsBucket.DAY),
    DAYS_30("30d", 30, AnalyticsBucket.DAY),
    DAYS_90("90d", 90, AnalyticsBucket.DAY),
    ALL("all", null, AnalyticsBucket.MONTH);

    private final String apiValue;
    private final Integer days;
    private final AnalyticsBucket bucket;

    AnalyticsRange(String apiValue, Integer days, AnalyticsBucket bucket) {
        this.apiValue = apiValue;
        this.days = days;
        this.bucket = bucket;
    }

    public String apiValue() {
        return apiValue;
    }

    public AnalyticsBucket bucket() {
        return bucket;
    }

    public Instant from(Instant now) {
        if (days == null) {
            return null;
        }
        return now.minus(days - 1L, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
    }

    public static AnalyticsRange fromApiValue(String value) {
        return Arrays.stream(values())
            .filter(range -> range.apiValue.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported analytics range: " + value));
    }
}
