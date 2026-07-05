package com.cloudcomment.moderation.domain;

import java.time.Instant;
import java.util.UUID;

public record Comment(
    UUID id,
    UUID siteId,
    UUID pageId,
    String pageUrl,
    UUID parentId,
    ParentComment parent,
    CommentAuthor author,
    String body,
    CommentStatus status,
    String moderationReason,
    Instant createdAt,
    Instant updatedAt
) {
}
