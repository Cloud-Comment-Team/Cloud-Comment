package com.cloudcomment.discussion.api;

record CreateOwnerReplyResponse(
    DiscussionMessageResponse message,
    boolean created
) {
}
