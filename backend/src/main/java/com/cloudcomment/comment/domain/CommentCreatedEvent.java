package com.cloudcomment.comment.domain;

import java.time.Instant;
import java.util.UUID;

public record CommentCreatedEvent(
    UUID siteId,
    UUID pageId,
    UUID commentId,
    UUID parentId,
    UUID authorUserId,
    boolean ownerReply,
    String authorEmail,
    String content,
    CommentStatus status,
    Instant createdAt
) {

    public CommentCreatedEvent(
        UUID siteId,
        UUID pageId,
        UUID commentId,
        UUID parentId,
        String authorEmail,
        String content,
        CommentStatus status,
        Instant createdAt
    ) {
        this(siteId, pageId, commentId, parentId, null, false, authorEmail, content, status, createdAt);
    }
}
