package com.cloudcomment.shared.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(name = "cloud-comment.mail.mode", havingValue = "log", matchIfMissing = true)
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    private final AtomicReference<MailMessage> lastSentMessage = new AtomicReference<>();

    @Override
    public void send(MailMessage message) {
        lastSentMessage.set(message);
        log.info(
            "Mail suppressed in log mode: to={} subject={} bodyLength={}",
            message.to(),
            message.subject(),
            message.textBody().length()
        );
    }

    public MailMessage lastSentMessage() {
        return lastSentMessage.get();
    }
}
