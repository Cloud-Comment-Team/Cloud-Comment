package com.cloudcomment.shared.mail;

public record MailMessage(
    String to,
    String subject,
    String textBody
) {
}
