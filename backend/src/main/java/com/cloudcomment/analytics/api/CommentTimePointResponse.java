package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.domain.CommentTimePoint;

import java.time.LocalDate;

record CommentTimePointResponse(
    LocalDate bucket,
    long total,
    long approved,
    long pending,
    long spam
) {

    static CommentTimePointResponse from(CommentTimePoint point) {
        return new CommentTimePointResponse(
            point.bucket(),
            point.total(),
            point.approved(),
            point.pending(),
            point.spam()
        );
    }
}
