package com.cloudcomment.automoderation.domain;

public record AutoModerationSignalSnapshot(
    String code,
    int score
) {
}
