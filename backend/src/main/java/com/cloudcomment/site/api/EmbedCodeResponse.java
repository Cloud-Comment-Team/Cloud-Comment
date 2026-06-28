package com.cloudcomment.site.api;

import com.cloudcomment.site.application.EmbedCode;

import java.util.Map;
import java.util.UUID;

public record EmbedCodeResponse(
    UUID siteId,
    String scriptUrl,
    String embedCode,
    Map<String, String> dataAttributes
) {

    static EmbedCodeResponse from(EmbedCode embedCode) {
        return new EmbedCodeResponse(
            embedCode.siteId(),
            embedCode.scriptUrl(),
            embedCode.embedCode(),
            embedCode.dataAttributes()
        );
    }
}
