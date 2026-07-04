package com.cloudcomment.moderation.api;

import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentResponse(
    UUID id,
    UUID siteId,
    UUID pageId,
    String pageUrl,
    UUID parentId,
    ParentCommentResponse parent,
    CommentAuthorResponse author,
    String content,
    CommentStatus status,
    Instant createdAt,
    Instant updatedAt,
    List<CommentResponse> replies
) {

    public CommentResponse {
        replies = List.copyOf(replies);
    }

    static CommentResponse from(Comment comment) {
        return new CommentResponse(
            comment.id(),
            comment.siteId(),
            comment.pageId(),
            comment.pageUrl(),
            comment.parentId(),
            ParentCommentResponse.from(comment.parent()),
            CommentAuthorResponse.from(comment.author()),
            comment.body(),
            comment.status(),
            comment.createdAt(),
            comment.updatedAt(),
            List.of()
        );
    }
}
