package com.cloudcomment.comment.domain;

import java.time.Instant;
import java.util.UUID;

public record CommentCreatedEvent(
    UUID siteId,
    UUID pageId,
    UUID commentId,
    UUID parentId,
    String authorEmail,
    String content,
    CommentStatus status,
    Instant createdAt
) {
}
