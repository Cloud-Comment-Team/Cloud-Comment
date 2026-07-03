package com.cloudcomment.account.application;

public record RelatedPersonalDataAnonymization(
    int ownedSitesDeleted,
    int authoredCommentsAnonymized,
    int moderationActionsAnonymized
) {
}
