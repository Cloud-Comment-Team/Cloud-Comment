package com.cloudcomment.moderation.api;

import com.cloudcomment.automoderation.api.AutoModerationFeedbackResponse;
import com.cloudcomment.automoderation.domain.AutoModerationCommentMetadata;
import com.cloudcomment.automoderation.domain.AutoModerationDecisionType;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CommentAutoModerationResponse(
    UUID policyVersionId,
    int policyVersion,
    AutoModerationExecutionMode executionMode,
    AutoModerationDecisionType decision,
    int score,
    String reason,
    List<AutoModerationSignalResponse> signals,
    Instant evaluatedAt,
    AutoModerationFeedbackResponse feedback
) {

    static CommentAutoModerationResponse from(AutoModerationCommentMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return new CommentAutoModerationResponse(
            metadata.policyVersionId(),
            metadata.policyVersion(),
            metadata.executionMode(),
            metadata.decision(),
            metadata.score(),
            metadata.reason(),
            metadata.signals().stream().map(AutoModerationSignalResponse::from).toList(),
            metadata.evaluatedAt(),
            metadata.feedback() != null
                ? new AutoModerationFeedbackResponse(
                    metadata.feedback().type(), metadata.feedback().createdAt()
                )
                : null
        );
    }
}
