package com.cloudcomment.automoderation.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

record PublishAutoModerationPolicyRequest(
    @NotNull @Min(1) Integer expectedRevision,
    @NotNull UUID expectedActiveVersionId
) {
}
