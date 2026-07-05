package com.cloudcomment.comment.application;

public record AutoModerationSignal(
    String category,
    int score,
    String reason
) {
}
