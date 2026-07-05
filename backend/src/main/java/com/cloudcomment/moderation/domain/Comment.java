package com.cloudcomment.moderation.domain;

import java.time.Instant;
import java.util.List;
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
    ModerationPriority priority,
    int priorityScore,
    List<String> priorityReasons,
    Instant createdAt,
    Instant updatedAt
) {

    public Comment {
        priorityReasons = priorityReasons != null ? List.copyOf(priorityReasons) : List.of();
    }
}
