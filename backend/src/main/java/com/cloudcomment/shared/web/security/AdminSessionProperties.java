package com.cloudcomment.shared.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud-comment.auth.admin-session")
public record AdminSessionProperties(
    String cookieName,
    String csrfCookieName,
    String csrfHeaderName,
    boolean secure
) {

    public AdminSessionProperties {
        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalArgumentException("Admin session cookie name must not be blank");
        }
        if (csrfCookieName == null || csrfCookieName.isBlank()) {
            throw new IllegalArgumentException("Admin CSRF cookie name must not be blank");
        }
        if (csrfHeaderName == null || csrfHeaderName.isBlank()) {
            throw new IllegalArgumentException("Admin CSRF header name must not be blank");
        }
        if ((cookieName.startsWith("__Host-") || csrfCookieName.startsWith("__Host-")) && !secure) {
            throw new IllegalArgumentException("__Host- cookies require Secure=true");
        }
    }
}
