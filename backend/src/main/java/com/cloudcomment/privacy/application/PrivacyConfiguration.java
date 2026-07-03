package com.cloudcomment.privacy.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PrivacyProperties.class)
public class PrivacyConfiguration {
}
