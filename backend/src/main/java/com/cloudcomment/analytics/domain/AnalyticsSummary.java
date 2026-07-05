package com.cloudcomment.analytics.domain;

public record AnalyticsSummary(
    long sites,
    long pages,
    long comments,
    long replies,
    long reactions,
    long pending,
    long approved,
    long rejected,
    long hidden,
    long spam
) {
}
