package com.cloudcomment.widgetcontext.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WidgetExchangeRequest(
    @NotBlank @Size(max = 128) String ticket,
    @NotBlank @Size(max = 128) String proof
) {
}
