package com.cloudcomment.automoderation.application;

import com.cloudcomment.automoderation.domain.AutoModerationDecisionType;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationSignalSnapshot;
import com.cloudcomment.comment.application.AutoModerationSignal;
import com.cloudcomment.comment.domain.CommentStatus;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

public record AutoModerationEvaluation(
    UUID policyVersionId,
    AutoModerationExecutionMode executionMode,
    int score,
    AutoModerationDecisionType decision,
    CommentStatus baselineStatus,
    CommentStatus effectiveStatus,
    boolean applied,
    String reason,
    List<AutoModerationSignal> signals,
    Instant evaluatedAt
) {

    public AutoModerationEvaluation {
        signals = signals != null ? List.copyOf(signals) : List.of();
    }

    public List<AutoModerationSignalSnapshot> safeSignals() {
        return signals.stream()
            .map(signal -> new AutoModerationSignalSnapshot(signal.category(), signal.score()))
            .toList();
    }
}
