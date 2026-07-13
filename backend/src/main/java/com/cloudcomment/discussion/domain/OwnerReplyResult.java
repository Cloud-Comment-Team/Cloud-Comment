package com.cloudcomment.discussion.domain;

import java.util.UUID;

public record OwnerReplyResult(
    DiscussionMessage message,
    UUID siteId,
    UUID pageId,
    boolean created
) {
}
