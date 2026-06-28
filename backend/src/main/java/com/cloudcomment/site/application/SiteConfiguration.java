package com.cloudcomment.site.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmbedCodeProperties.class)
class SiteConfiguration {
}
