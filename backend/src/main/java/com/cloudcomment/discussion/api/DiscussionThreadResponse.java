package com.cloudcomment.discussion.api;

import com.cloudcomment.discussion.domain.DiscussionThread;

import java.util.List;

record DiscussionThreadResponse(
    DiscussionSummaryResponse summary,
    List<DiscussionMessageResponse> messages
) {
    DiscussionThreadResponse {
        messages = List.copyOf(messages);
    }

    static DiscussionThreadResponse from(DiscussionThread thread) {
        return new DiscussionThreadResponse(
            DiscussionSummaryResponse.from(thread.summary()),
            thread.messages().stream().map(DiscussionMessageResponse::from).toList()
        );
    }
}
