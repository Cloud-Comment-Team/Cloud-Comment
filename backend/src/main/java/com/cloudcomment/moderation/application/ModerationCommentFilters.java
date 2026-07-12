package com.cloudcomment.moderation.application;

import com.cloudcomment.moderation.domain.CommentSortField;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.SortOrder;

import java.time.Instant;
import java.util.UUID;

public record ModerationCommentFilters(
    UUID siteId,
    UUID pageId,
    String pageUrl,
    CommentStatus status,
    Instant createdFrom,
    Instant createdTo,
    String search,
    Boolean favorite,
    CommentSortField sortBy,
    SortOrder sortOrder
) {
}
