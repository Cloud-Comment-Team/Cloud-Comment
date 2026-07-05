package com.cloudcomment.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

@ConfigurationProperties(prefix = "cloud-comment.demo.seed")
public record DemoDataProperties(
    boolean enabled,
    String ownerEmail,
    String ownerPassword,
    UUID siteId,
    List<String> allowedOrigins,
    String pageUrl
) {

    private static final String DEFAULT_OWNER_EMAIL = "demo-owner@cloudcomment.local";
    private static final String DEFAULT_OWNER_PASSWORD = "CloudCommentDemo!2026";
    private static final UUID DEFAULT_SITE_ID = UUID.fromString("c680d246-8876-4049-975c-73ccf029408f");
    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
        "http://localhost",
        "http://127.0.0.1",
        "http://127.0.0.1:4173"
    );
    private static final String DEFAULT_PAGE_URL = "http://localhost/demo-page.html";

    public DemoDataProperties {
        ownerEmail = hasText(ownerEmail) ? ownerEmail.trim().toLowerCase() : DEFAULT_OWNER_EMAIL;
        ownerPassword = hasText(ownerPassword) ? ownerPassword : DEFAULT_OWNER_PASSWORD;
        siteId = siteId != null ? siteId : DEFAULT_SITE_ID;
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
            ? DEFAULT_ALLOWED_ORIGINS
            : List.copyOf(allowedOrigins);
        pageUrl = hasText(pageUrl) ? pageUrl.trim() : DEFAULT_PAGE_URL;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
