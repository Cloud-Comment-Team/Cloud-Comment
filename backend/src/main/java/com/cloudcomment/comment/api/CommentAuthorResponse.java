package com.cloudcomment.comment.api;

import com.cloudcomment.comment.domain.CommentAuthor;

import java.util.UUID;

record CommentAuthorResponse(
    UUID id,
    String email,
    String displayName
) {

    static CommentAuthorResponse from(CommentAuthor author) {
        return new CommentAuthorResponse(author.id(), author.email(), author.displayName());
    }
}
