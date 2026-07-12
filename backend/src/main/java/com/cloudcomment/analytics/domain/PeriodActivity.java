package com.cloudcomment.analytics.domain;

public record PeriodActivity(
    long comments,
    long reactions,
    long automaticDecisions,
    long manualDecisions,
    long undoActions
) {
}
