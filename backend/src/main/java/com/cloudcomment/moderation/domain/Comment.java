package com.cloudcomment.moderation.domain;

import com.cloudcomment.automoderation.domain.AutoModerationCommentMetadata;
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
    boolean pinned,
    boolean favorite,
    ModerationPriority priority,
    int priorityScore,
    List<String> priorityReasons,
    AutoModerationCommentMetadata autoModeration,
    Instant createdAt,
    Instant updatedAt
) {

    public Comment {
        priorityReasons = priorityReasons != null ? List.copyOf(priorityReasons) : List.of();
    }

    public Comment(
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
        boolean pinned,
        boolean favorite,
        ModerationPriority priority,
        int priorityScore,
        List<String> priorityReasons,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id, siteId, pageId, pageUrl, parentId, parent, author, body, status, moderationReason,
            pinned, favorite, priority, priorityScore, priorityReasons, null, createdAt, updatedAt
        );
    }
}
