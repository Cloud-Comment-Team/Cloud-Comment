package com.cloudcomment.comment.application;

import com.cloudcomment.comment.domain.CommentStatus;

import java.util.List;

public record AutoModerationDecision(
    CommentStatus status,
    String reason,
    int score,
    List<AutoModerationSignal> signals
) {

    public AutoModerationDecision {
        signals = signals != null ? List.copyOf(signals) : List.of();
    }
}
