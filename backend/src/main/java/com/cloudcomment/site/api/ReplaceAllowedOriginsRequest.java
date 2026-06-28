package com.cloudcomment.site.api;

import com.cloudcomment.site.api.validation.ValidHttpOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceAllowedOriginsRequest(
    @NotEmpty
    @Size(max = 20)
    List<@NotBlank @Size(max = 255) @ValidHttpOrigin String> allowedOrigins
) {
}
