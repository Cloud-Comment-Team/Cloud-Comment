package com.cloudcomment.account.application;

public record AccountDeletionReport(
    int ownedSitesDeleted,
    int authoredCommentsAnonymized,
    int moderationActionsAnonymized,
    int sessionsRevoked
) {
}
