package com.cloudcomment.automoderation.api;

import com.cloudcomment.automoderation.application.AutoModerationEvaluation;
import com.cloudcomment.automoderation.domain.AutoModerationDecisionType;
import com.cloudcomment.comment.domain.CommentStatus;

import java.util.List;

record AutoModerationSimulationResponse(
    int score,
    AutoModerationDecisionType decision,
    CommentStatus baselineStatus,
    CommentStatus effectiveStatus,
    boolean applied,
    String reason,
    List<AutoModerationSimulationSignalResponse> signals
) {

    static AutoModerationSimulationResponse from(AutoModerationEvaluation evaluation) {
        return new AutoModerationSimulationResponse(
            evaluation.score(),
            evaluation.decision(),
            evaluation.baselineStatus(),
            evaluation.effectiveStatus(),
            evaluation.applied(),
            evaluation.reason(),
            evaluation.signals().stream().map(AutoModerationSimulationSignalResponse::from).toList()
        );
    }
}
