package com.cloudcomment.site.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record AutoModerationCheckRequest(
    @NotBlank
    @Size(max = 5_000)
    String content
) {
}
