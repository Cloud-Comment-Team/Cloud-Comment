package com.cloudcomment.discussion.application;

import com.cloudcomment.discussion.domain.DiscussionFilter;

import java.util.UUID;

public record DiscussionFilters(
    UUID siteId,
    DiscussionFilter view,
    String search
) {
}
