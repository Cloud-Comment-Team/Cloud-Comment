package com.cloudcomment.site.api;

import com.cloudcomment.site.api.validation.ValidDomainName;
import com.cloudcomment.site.domain.ModerationMode;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSiteRequest(
    @Size(max = 160)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    String name,

    @Size(max = 255)
    @ValidDomainName
    String domain,

    ModerationMode moderationMode,

    Boolean isActive
) {
}
