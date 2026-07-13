package com.cloudcomment.discussion.application;

import com.cloudcomment.discussion.domain.DiscussionSummary;

import java.util.List;

public record DiscussionPage(
    List<DiscussionSummary> items,
    int page,
    int pageSize,
    long totalItems
) {
    public DiscussionPage {
        items = List.copyOf(items);
    }
}
