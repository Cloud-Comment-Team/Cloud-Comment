package com.cloudcomment.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DemoDataProperties.class)
class DemoDataConfiguration {
}
