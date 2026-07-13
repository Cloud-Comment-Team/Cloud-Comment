package com.cloudcomment.discussion.domain;

import java.util.List;

public record DiscussionThread(
    DiscussionSummary summary,
    List<DiscussionMessage> messages
) {
    public DiscussionThread {
        messages = List.copyOf(messages);
    }
}
