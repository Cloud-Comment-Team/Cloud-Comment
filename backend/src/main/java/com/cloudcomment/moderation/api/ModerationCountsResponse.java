package com.cloudcomment.moderation.api;

import com.cloudcomment.moderation.domain.CommentStatus;

import java.util.Map;

public record ModerationCountsResponse(Map<CommentStatus, Long> statuses, long requiringDecision) {
    static ModerationCountsResponse from(Map<CommentStatus, Long> counts) {
        return new ModerationCountsResponse(
            Map.copyOf(counts), counts.getOrDefault(CommentStatus.PENDING, 0L) + counts.getOrDefault(CommentStatus.SPAM, 0L)
        );
    }
}
