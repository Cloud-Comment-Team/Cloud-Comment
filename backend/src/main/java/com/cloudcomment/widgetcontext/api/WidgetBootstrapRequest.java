package com.cloudcomment.widgetcontext.api;

import com.cloudcomment.comment.api.validation.ValidPageUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WidgetBootstrapRequest(
    @NotBlank @Size(max = 512) String publicKey,
    @NotBlank @Size(max = 2048) @ValidPageUrl String pageUrl
) {
}
