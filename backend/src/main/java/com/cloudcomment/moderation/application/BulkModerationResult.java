package com.cloudcomment.moderation.application;

import com.cloudcomment.moderation.domain.ModerationAction;

import java.util.UUID;

public record BulkModerationResult(UUID commentId, boolean success, ModerationAction action, String errorCode, String message) {
    public static BulkModerationResult success(UUID commentId, ModerationAction action) {
        return new BulkModerationResult(commentId, true, action, null, null);
    }

    public static BulkModerationResult failure(UUID commentId, String errorCode, String message) {
        return new BulkModerationResult(commentId, false, null, errorCode, message);
    }
}
