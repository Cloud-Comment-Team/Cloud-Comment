package com.cloudcomment.comment.application;

import com.cloudcomment.comment.domain.CommentView;

import java.util.List;

public record CommentPage(
    List<CommentView> items,
    int page,
    int pageSize,
    long totalItems
) {

    public CommentPage {
        items = List.copyOf(items);
    }
}
