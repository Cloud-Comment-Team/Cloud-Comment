package com.cloudcomment.moderation.api;

import com.cloudcomment.moderation.application.BulkModerationResult;

import java.util.List;

public record BulkModerationResponse(List<Item> items) {
    public BulkModerationResponse { items = List.copyOf(items); }

    static BulkModerationResponse from(List<BulkModerationResult> results) {
        return new BulkModerationResponse(results.stream().map(result -> new Item(
            result.commentId(), result.success(),
            result.action() != null ? ModerationActionResponse.from(result.action()) : null,
            result.errorCode(), result.message()
        )).toList());
    }

    public record Item(java.util.UUID commentId, boolean success, ModerationActionResponse action, String errorCode, String message) {}
}
