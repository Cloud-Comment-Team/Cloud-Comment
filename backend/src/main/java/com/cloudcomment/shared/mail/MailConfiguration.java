package com.cloudcomment.shared.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {

    @Bean
    @ConditionalOnProperty(name = "cloud-comment.mail.mode", havingValue = "smtp")
    JavaMailSender javaMailSender(MailProperties mailProperties) {
        MailProperties.Smtp smtp = mailProperties.smtp();
        if (smtp.host() == null || smtp.host().isBlank()) {
            throw new IllegalStateException("cloud-comment.mail.smtp.host is required when mail.mode=smtp");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        sender.setPort(smtp.port());
        if (smtp.username() != null && !smtp.username().isBlank()) {
            sender.setUsername(smtp.username());
        }
        if (smtp.password() != null && !smtp.password().isBlank()) {
            sender.setPassword(smtp.password());
        }

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", smtp.username() != null && !smtp.username().isBlank());
        properties.put("mail.smtp.starttls.enable", smtp.starttls());
        return sender;
    }
}
