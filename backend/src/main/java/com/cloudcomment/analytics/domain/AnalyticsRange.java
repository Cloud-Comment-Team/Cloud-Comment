package com.cloudcomment.analytics.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

public enum AnalyticsRange {
    DAYS_7("7d", 7, AnalyticsBucket.DAY),
    DAYS_30("30d", 30, AnalyticsBucket.DAY),
    DAYS_90("90d", 90, AnalyticsBucket.WEEK),
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

    public Instant from(Instant now, ZoneId zoneId) {
        if (days == null) {
            return null;
        }
        LocalDate currentDate = now.atZone(zoneId).toLocalDate();
        return currentDate.minusDays(days - 1L).atStartOfDay(zoneId).toInstant();
    }

    public Instant previousFrom(Instant now, ZoneId zoneId) {
        if (days == null) {
            return null;
        }
        return from(now, zoneId).atZone(zoneId).minusDays(days).toInstant();
    }

    public Instant previousTo(Instant now, ZoneId zoneId) {
        if (days == null) {
            return null;
        }
        ZonedDateTime zonedNow = now.atZone(zoneId);
        return zonedNow.minusDays(days).toInstant();
    }

    public boolean supportsComparison() {
        return days != null;
    }

    public static AnalyticsRange fromApiValue(String value) {
        return Arrays.stream(values())
            .filter(range -> range.apiValue.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported analytics range: " + value));
    }
}
