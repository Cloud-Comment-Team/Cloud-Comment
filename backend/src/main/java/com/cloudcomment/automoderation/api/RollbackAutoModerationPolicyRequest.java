package com.cloudcomment.automoderation.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

record RollbackAutoModerationPolicyRequest(
    @NotNull UUID expectedActiveVersionId
) {
}
