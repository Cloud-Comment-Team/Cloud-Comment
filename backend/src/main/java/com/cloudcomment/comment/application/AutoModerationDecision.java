package com.cloudcomment.comment.application;

import com.cloudcomment.comment.domain.CommentStatus;

public record AutoModerationDecision(
    CommentStatus status,
    String reason
) {
}
