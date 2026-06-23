package com.cloudcomment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentConfigurationTests {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void localProfileKeepsDevelopmentDatasourceDefaults() throws IOException {
        PropertySource<?> source = load("application-local.yml");

        assertThat(source.getProperty("spring.datasource.url"))
            .isEqualTo("${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/cloud_comment}");
        assertThat(source.getProperty("spring.datasource.username"))
            .isEqualTo("${SPRING_DATASOURCE_USERNAME:cloud_comment}");
        assertThat(source.getProperty("spring.datasource.password"))
            .isEqualTo("${SPRING_DATASOURCE_PASSWORD:cloud_comment}");
    }

    @Test
    void defaultProfileIsLocalForDevelopment() throws IOException {
        PropertySource<?> source = load("application.yml");

        assertThat(source.getProperty("spring.profiles.default")).isEqualTo("local");
    }

    @Test
    void prodProfileRequiresDatasourceEnvironmentVariables() throws IOException {
        PropertySource<?> source = load("application-prod.yml");

        assertThat(source.getProperty("spring.datasource.url")).isEqualTo("${SPRING_DATASOURCE_URL}");
        assertThat(source.getProperty("spring.datasource.username")).isEqualTo("${SPRING_DATASOURCE_USERNAME}");
        assertThat(source.getProperty("spring.datasource.password")).isEqualTo("${SPRING_DATASOURCE_PASSWORD}");
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        return loader.load(resourceName, new ClassPathResource(resourceName)).get(0);
    }
}
