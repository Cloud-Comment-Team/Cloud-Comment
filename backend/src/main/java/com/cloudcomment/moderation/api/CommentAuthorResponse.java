package com.cloudcomment.moderation.api;

import com.cloudcomment.moderation.domain.CommentAuthor;

import java.util.UUID;

public record CommentAuthorResponse(
    UUID id,
    String email,
    String displayName
) {

    static CommentAuthorResponse from(CommentAuthor author) {
        if (author == null) {
            return null;
        }
        return new CommentAuthorResponse(author.id(), author.email(), author.displayName());
    }
}
