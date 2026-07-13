package com.cloudcomment.comment.api;

import com.cloudcomment.comment.application.CommentPermalinkLocation;

import java.util.UUID;

record CommentPermalinkResponse(
    UUID commentId,
    UUID rootCommentId,
    int rootPage,
    Integer replyPage
) {
    static CommentPermalinkResponse from(CommentPermalinkLocation location) {
        return new CommentPermalinkResponse(
            location.commentId(),
            location.rootCommentId(),
            location.rootPage(),
            location.replyPage()
        );
    }
}
