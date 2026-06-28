package com.cloudcomment.comment.api;

import com.cloudcomment.comment.api.validation.ValidPageUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

record CreateCommentRequest(
    @NotBlank
    @Size(max = 2048)
    @ValidPageUrl
    String pageUrl,

    UUID parentId,

    @NotBlank
    @Size(max = 5_000)
    String content
) {
}
