package com.cloudcomment.site.application;

import java.util.Map;
import java.util.UUID;

public record EmbedCode(
    UUID siteId,
    String scriptUrl,
    String embedCode,
    Map<String, String> dataAttributes
) {

    public EmbedCode {
        dataAttributes = Map.copyOf(dataAttributes);
    }
}
