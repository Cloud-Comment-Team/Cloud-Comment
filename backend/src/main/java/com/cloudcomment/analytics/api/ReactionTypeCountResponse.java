package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.ReactionTypeCount;

record ReactionTypeCountResponse(
    String type,
    long count
) {

    static ReactionTypeCountResponse from(ReactionTypeCount count) {
        return new ReactionTypeCountResponse(count.type(), count.count());
    }
}
