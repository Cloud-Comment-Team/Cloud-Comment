package com.cloudcomment.comment.api;

import com.cloudcomment.comment.domain.CommentReactionType;

record CommentReactionRequest(
    CommentReactionType type
) {
}
