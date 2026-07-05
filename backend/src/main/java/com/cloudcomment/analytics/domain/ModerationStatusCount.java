package com.cloudcomment.analytics.domain;

public record ModerationStatusCount(
    String status,
    long count
) {
}
