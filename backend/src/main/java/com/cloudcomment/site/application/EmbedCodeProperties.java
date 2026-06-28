package com.cloudcomment.site.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud-comment.embed")
public record EmbedCodeProperties(
    String scriptUrl,
    String apiBaseUrl
) {
}
