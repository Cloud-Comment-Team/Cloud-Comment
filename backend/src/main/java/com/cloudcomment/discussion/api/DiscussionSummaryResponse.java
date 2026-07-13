package com.cloudcomment.discussion.api;

import com.cloudcomment.discussion.domain.DiscussionStatus;
import com.cloudcomment.discussion.domain.DiscussionSummary;

import java.time.Instant;
import java.util.UUID;

record DiscussionSummaryResponse(
    UUID rootCommentId,
    UUID siteId,
    String siteName,
    UUID pageId,
    String pageUrl,
    String pageTitle,
    DiscussionAuthorResponse lastAuthor,
    String lastMessage,
    Instant lastActivityAt,
    long replyCount,
    boolean unread,
    DiscussionStatus status,
    boolean pinned
) {
    static DiscussionSummaryResponse from(DiscussionSummary summary) {
        return new DiscussionSummaryResponse(
            summary.rootCommentId(), summary.siteId(), summary.siteName(), summary.pageId(), summary.pageUrl(),
            summary.pageTitle(), DiscussionAuthorResponse.from(summary.lastAuthor()), summary.lastMessage(),
            summary.lastActivityAt(), summary.replyCount(), summary.unread(), summary.status(), summary.pinned()
        );
    }
}
