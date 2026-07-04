package com.cloudcomment.comment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record UpdateCommentRequest(
    @NotBlank
    @Size(max = 5_000)
    String content
) {
}
