package com.cloudcomment.moderation.application;

import com.cloudcomment.moderation.domain.Comment;

import java.util.List;

public record ModerationCommentPage(
    List<Comment> items,
    int page,
    int pageSize,
    long totalItems
) {

    public ModerationCommentPage {
        items = List.copyOf(items);
    }
}
