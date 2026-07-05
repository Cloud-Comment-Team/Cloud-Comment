package com.cloudcomment.analytics.domain;

import java.time.LocalDate;

public record CommentTimePoint(
    LocalDate bucket,
    long total,
    long approved,
    long pending,
    long spam
) {
}
