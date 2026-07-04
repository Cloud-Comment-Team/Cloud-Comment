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
    List<CommentReactionSummary> reactions,
    List<CommentView> replies
) {

    public CommentView {
        reactions = List.copyOf(reactions);
        replies = List.copyOf(replies);
    }

    public CommentView(
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
        this(
            id,
            siteId,
            pageId,
            parentId,
            author,
            content,
            status,
            createdAt,
            updatedAt,
            List.of(),
            replies
        );
    }
}
