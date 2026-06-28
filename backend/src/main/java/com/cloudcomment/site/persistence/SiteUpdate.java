package com.cloudcomment.site.persistence;

import com.cloudcomment.site.domain.ModerationMode;

public record SiteUpdate(
    String name,
    String domain,
    ModerationMode moderationMode,
    Boolean active
) {

    public boolean hasChanges() {
        return name != null || domain != null || moderationMode != null || active != null;
    }
}
