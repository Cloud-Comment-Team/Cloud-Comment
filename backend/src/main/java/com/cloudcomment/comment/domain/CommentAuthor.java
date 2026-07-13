package com.cloudcomment.comment.domain;

import java.util.UUID;

public record CommentAuthor(
    UUID id,
    String email,
    String displayName,
    CommentAuthorKind kind
) {

    public CommentAuthor(UUID id, String email, String displayName) {
        this(id, email, displayName, CommentAuthorKind.VISITOR);
    }
}
