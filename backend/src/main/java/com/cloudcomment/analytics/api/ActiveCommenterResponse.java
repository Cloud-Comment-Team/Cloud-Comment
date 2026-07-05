package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.ActiveCommenter;

import java.time.Instant;
import java.util.UUID;

record ActiveCommenterResponse(
    UUID userId,
    String email,
    String displayName,
    long comments,
    long approved,
    long pending,
    long rejectedOrSpam,
    Instant lastActivityAt
) {

    static ActiveCommenterResponse from(ActiveCommenter commenter) {
        return new ActiveCommenterResponse(
            commenter.userId(),
            commenter.email(),
            commenter.displayName(),
            commenter.comments(),
            commenter.approved(),
            commenter.pending(),
            commenter.rejectedOrSpam(),
            commenter.lastActivityAt()
        );
    }
}
