package com.cloudcomment.automoderation.domain;

import com.cloudcomment.comment.domain.CommentStatus;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

public record AutoModerationSnapshot(
    UUID policyVersionId,
    AutoModerationExecutionMode executionMode,
    int score,
    AutoModerationDecisionType decision,
    List<AutoModerationSignalSnapshot> signals,
    String reason,
    CommentStatus appliedStatus,
    Instant evaluatedAt
) {

    public AutoModerationSnapshot {
        signals = signals != null ? List.copyOf(signals) : List.of();
    }
}
