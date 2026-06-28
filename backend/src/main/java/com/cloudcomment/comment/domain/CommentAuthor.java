package com.cloudcomment.comment.domain;

import java.util.UUID;

public record CommentAuthor(
    UUID id,
    String email,
    String displayName
) {
}
