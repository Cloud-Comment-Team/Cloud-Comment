package com.cloudcomment.privacy.application;

public record PrivacyRetentionReport(
    int expiredDeletionRequestsCancelled,
    int oldDeletionRequestsDeleted,
    int inactiveSessionsDeleted
) {
}
