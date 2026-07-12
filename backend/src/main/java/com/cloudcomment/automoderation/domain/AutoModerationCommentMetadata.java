package com.cloudcomment.automoderation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AutoModerationCommentMetadata(
    UUID policyVersionId,
    int policyVersion,
    AutoModerationExecutionMode executionMode,
    AutoModerationDecisionType decision,
    int score,
    String reason,
    List<AutoModerationSignalSnapshot> signals,
    Instant evaluatedAt,
    AutoModerationFeedback feedback
) {

    public AutoModerationCommentMetadata {
        signals = signals != null ? List.copyOf(signals) : List.of();
    }
}
