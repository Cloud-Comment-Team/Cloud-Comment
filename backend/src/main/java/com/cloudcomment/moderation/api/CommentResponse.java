package com.cloudcomment.moderation.api;

import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationPriority;

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
    String moderationReason,
    boolean pinned,
    boolean favorite,
    ModerationPriority priority,
    int priorityScore,
    List<String> priorityReasons,
    Instant createdAt,
    Instant updatedAt,
    List<CommentResponse> replies
) {

    public CommentResponse {
        priorityReasons = priorityReasons != null ? List.copyOf(priorityReasons) : List.of();
        replies = replies != null ? List.copyOf(replies) : List.of();
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
            comment.moderationReason(),
            comment.pinned(),
            comment.favorite(),
            comment.priority(),
            comment.priorityScore(),
            comment.priorityReasons(),
            comment.createdAt(),
            comment.updatedAt(),
            List.of()
        );
    }
}
