package com.cloudcomment.comment.api;

import com.cloudcomment.comment.domain.CommentReactionSummary;

import java.util.List;

record CommentReactionsResponse(
    List<CommentReactionResponse> reactions
) {

    CommentReactionsResponse {
        reactions = List.copyOf(reactions);
    }

    static CommentReactionsResponse from(List<CommentReactionSummary> summaries) {
        return new CommentReactionsResponse(summaries.stream().map(CommentReactionResponse::from).toList());
    }
}
