package com.cloudcomment.shared.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud-comment.mail")
public record MailProperties(
    String mode,
    String from,
    String confirmationBaseUrl,
    Smtp smtp
) {

    public MailProperties {
        if (mode == null || mode.isBlank()) {
            mode = "log";
        }
        if (from == null || from.isBlank()) {
            from = "noreply@cloud-comment.local";
        }
        if (confirmationBaseUrl == null || confirmationBaseUrl.isBlank()) {
            confirmationBaseUrl = "http://localhost/account/deletion-confirm";
        }
        if (smtp == null) {
            smtp = new Smtp(null, 587, null, null, true);
        }
    }

    public record Smtp(
        String host,
        int port,
        String username,
        String password,
        boolean starttls
    ) {
    }
}
