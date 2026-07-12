package com.cloudcomment.automoderation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record SimulateAutoModerationPolicyRequest(
    @NotBlank @Size(max = 5_000) String content
) {
}
