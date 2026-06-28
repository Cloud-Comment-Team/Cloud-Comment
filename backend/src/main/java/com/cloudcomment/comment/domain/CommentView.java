package com.cloudcomment.comment.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentView(
    UUID id,
    UUID siteId,
    UUID pageId,
    UUID parentId,
    CommentAuthor author,
    String content,
    CommentStatus status,
    Instant createdAt,
    Instant updatedAt,
    List<CommentView> replies
) {

    public CommentView {
        replies = List.copyOf(replies);
    }
}
