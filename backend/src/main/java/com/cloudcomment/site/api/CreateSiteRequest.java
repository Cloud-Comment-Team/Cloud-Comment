package com.cloudcomment.site.api;

import com.cloudcomment.site.api.validation.ValidDomainName;
import com.cloudcomment.site.api.validation.ValidHttpOrigin;
import com.cloudcomment.site.domain.ModerationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSiteRequest(
    @NotBlank
    @Size(max = 160)
    String name,

    @NotBlank
    @Size(max = 255)
    @ValidDomainName
    String domain,

    @NotNull
    ModerationMode moderationMode,

    @NotEmpty
    @Size(max = 20)
    List<@NotBlank @Size(max = 255) @ValidHttpOrigin String> allowedOrigins,

    @Valid
    WidgetStyleRequest widgetStyle,

    @Valid
    AutoModerationSettingsRequest autoModeration
) {
}
