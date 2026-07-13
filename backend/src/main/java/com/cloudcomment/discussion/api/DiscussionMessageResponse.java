package com.cloudcomment.discussion.api;

import com.cloudcomment.discussion.domain.DiscussionMessage;

import java.time.Instant;
import java.util.UUID;

record DiscussionMessageResponse(
    UUID id,
    UUID parentId,
    DiscussionAuthorResponse author,
    String content,
    Instant createdAt,
    Instant updatedAt,
    boolean pinned
) {
    static DiscussionMessageResponse from(DiscussionMessage message) {
        return new DiscussionMessageResponse(
            message.id(), message.parentId(), DiscussionAuthorResponse.from(message.author()), message.content(),
            message.createdAt(), message.updatedAt(), message.pinned()
        );
    }
}
