package com.cloudcomment.moderation.domain;

import java.util.UUID;

public record CommentAuthor(
    UUID id,
    String email,
    String displayName
) {
}
