package com.cloudcomment.moderation.application;

import com.cloudcomment.moderation.domain.CommentSortField;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.SortOrder;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record ModerationCommentFilters(
    UUID siteId,
    UUID pageId,
    String pageUrl,
    CommentStatus status,
    List<CommentStatus> statuses,
    Instant createdFrom,
    Instant createdTo,
    String search,
    Boolean favorite,
    CommentSortField sortBy,
    SortOrder sortOrder
) {
    public ModerationCommentFilters(
        UUID siteId, UUID pageId, String pageUrl, CommentStatus status, Instant createdFrom, Instant createdTo,
        String search, Boolean favorite, CommentSortField sortBy, SortOrder sortOrder
    ) {
        this(siteId, pageId, pageUrl, status, null, createdFrom, createdTo, search, favorite, sortBy, sortOrder);
    }
}
