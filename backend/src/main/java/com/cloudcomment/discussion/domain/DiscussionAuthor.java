package com.cloudcomment.discussion.domain;

import java.util.UUID;

public record DiscussionAuthor(
    UUID id,
    String displayName,
    boolean owner
) {
}
