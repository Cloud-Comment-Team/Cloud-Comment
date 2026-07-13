package com.cloudcomment.discussion.domain;

import java.time.Instant;
import java.util.UUID;

public record DiscussionSummary(
    UUID rootCommentId,
    UUID siteId,
    String siteName,
    UUID pageId,
    String pageUrl,
    String pageTitle,
    DiscussionAuthor lastAuthor,
    String lastMessage,
    Instant lastActivityAt,
    long replyCount,
    boolean unread,
    DiscussionStatus status,
    boolean pinned
) {
}
