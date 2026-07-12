package com.cloudcomment.comment.api;

import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.domain.CommentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CommentResponse(
    UUID id,
    UUID siteId,
    UUID pageId,
    UUID parentId,
    CommentAuthorResponse author,
    String content,
    CommentStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant editedAt,
    boolean pinned,
    boolean ownedByCurrentUser,
    List<CommentReactionResponse> reactions,
    long replyCount,
    List<CommentResponse> replies
) {

    CommentResponse {
        reactions = List.copyOf(reactions);
        replies = List.copyOf(replies);
    }

    static CommentResponse from(CommentView comment) {
        return new CommentResponse(
            comment.id(),
            comment.siteId(),
            comment.pageId(),
            comment.parentId(),
            CommentAuthorResponse.from(comment.author()),
            comment.content(),
            comment.status(),
            comment.createdAt(),
            comment.updatedAt(),
            comment.editedAt(),
            comment.pinned(),
            comment.ownedByCurrentUser(),
            comment.reactions().stream().map(CommentReactionResponse::from).toList(),
            comment.replyCount(),
            comment.replies().stream().map(CommentResponse::from).toList()
        );
    }
}
