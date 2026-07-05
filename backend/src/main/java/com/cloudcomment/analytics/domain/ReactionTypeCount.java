package com.cloudcomment.analytics.domain;

public record ReactionTypeCount(
    String type,
    long count
) {
}
