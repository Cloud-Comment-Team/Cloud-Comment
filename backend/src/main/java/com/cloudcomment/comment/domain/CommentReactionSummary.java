package com.cloudcomment.comment.domain;

public record CommentReactionSummary(
    CommentReactionType type,
    long count,
    boolean reactedByCurrentUser
) {
}
