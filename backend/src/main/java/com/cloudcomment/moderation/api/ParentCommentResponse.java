package com.cloudcomment.moderation.api;

import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ParentComment;

import java.time.Instant;
import java.util.UUID;

record ParentCommentResponse(
    UUID id,
    CommentAuthorResponse author,
    String content,
    CommentStatus status,
    Instant createdAt
) {

    static ParentCommentResponse from(ParentComment parent) {
        if (parent == null) {
            return null;
        }
        return new ParentCommentResponse(
            parent.id(),
            CommentAuthorResponse.from(parent.author()),
            parent.body(),
            parent.status(),
            parent.createdAt()
        );
    }
}
