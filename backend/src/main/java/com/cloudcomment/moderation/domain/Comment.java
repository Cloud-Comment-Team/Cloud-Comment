package com.cloudcomment.moderation.domain;

import java.time.Instant;
import java.util.UUID;

public record Comment(
    UUID id,
    UUID siteId,
    UUID pageId,
    String pageUrl,
    UUID parentId,
    CommentAuthor author,
    String body,
    CommentStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
