package com.cloudcomment.discussion.domain;

import java.time.Instant;
import java.util.UUID;

public record DiscussionMessage(
    UUID id,
    UUID parentId,
    DiscussionAuthor author,
    String content,
    Instant createdAt,
    Instant updatedAt,
    boolean pinned
) {
}
