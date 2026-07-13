package com.cloudcomment.discussion.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

record CreateOwnerReplyRequest(
    @NotNull UUID operationId,
    @NotBlank @Size(max = 5000) String content
) {
}
