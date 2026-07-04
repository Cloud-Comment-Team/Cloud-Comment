package com.cloudcomment.moderation.domain;

import java.time.Instant;
import java.util.UUID;

public record ParentComment(
    UUID id,
    CommentAuthor author,
    String body,
    CommentStatus status,
    Instant createdAt
) {
}
