package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.ModerationStatusCount;

record ModerationStatusCountResponse(
    String status,
    long count
) {

    static ModerationStatusCountResponse from(ModerationStatusCount count) {
        return new ModerationStatusCountResponse(count.status(), count.count());
    }
}
