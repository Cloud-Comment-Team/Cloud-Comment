package com.cloudcomment.discussion.api;

import com.cloudcomment.discussion.domain.DiscussionAuthor;

import java.util.UUID;

record DiscussionAuthorResponse(
    UUID id,
    String displayName,
    boolean owner
) {
    static DiscussionAuthorResponse from(DiscussionAuthor author) {
        return new DiscussionAuthorResponse(author.id(), author.displayName(), author.owner());
    }
}
