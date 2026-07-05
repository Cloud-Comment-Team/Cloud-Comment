package com.cloudcomment.site.api;

import com.cloudcomment.comment.application.AutoModerationDecision;
import com.cloudcomment.comment.domain.CommentStatus;

import java.util.List;

record AutoModerationCheckResponse(
    CommentStatus status,
    int score,
    String reason,
    List<AutoModerationSignalResponse> signals
) {

    AutoModerationCheckResponse {
        signals = List.copyOf(signals);
    }

    static AutoModerationCheckResponse from(AutoModerationDecision decision) {
        return new AutoModerationCheckResponse(
            decision.status(),
            decision.score(),
            decision.reason(),
            decision.signals().stream().map(AutoModerationSignalResponse::from).toList()
        );
    }
}
