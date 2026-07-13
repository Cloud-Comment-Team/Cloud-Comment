package com.cloudcomment.comment.application;

import java.util.UUID;

public record CommentPermalinkLocation(
    UUID commentId,
    UUID rootCommentId,
    int rootPage,
    Integer replyPage
) {
}
