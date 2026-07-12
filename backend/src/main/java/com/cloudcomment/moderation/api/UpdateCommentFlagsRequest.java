package com.cloudcomment.moderation.api;

public record UpdateCommentFlagsRequest(
    Boolean pinned,
    Boolean favorite
) {
}
