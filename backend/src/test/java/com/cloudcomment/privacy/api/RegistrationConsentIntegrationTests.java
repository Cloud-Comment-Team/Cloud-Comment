package com.cloudcomment.privacy.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.cloudcomment.privacy.application.ConsentTestSupport.PRIVACY_POLICY_VERSION;
import static com.cloudcomment.privacy.application.ConsentTestSupport.TERMS_VERSION;
import static com.cloudcomment.privacy.application.ConsentTestSupport.registerRequestJson;
import static com.cloudcomment.privacy.application.ConsentTestSupport.registerRequestJsonWithoutConsent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.cloudcomment.support.AdminSecurityTestSupport.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RegistrationConsentIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registerStoresConsentForAdminAndWidgetSources() throws Exception {
        String adminEmail = "admin-consent-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(adminEmail, "strong-password")))
            .andExpect(status().isCreated());

        Integer adminConsents = jdbcTemplate.queryForObject(
            """
                select count(*)
                from user_consents uc
                join app_users u on u.id = uc.user_id
                where u.email = ?
                  and uc.source = 'ADMIN'
                  and uc.privacy_policy_version = ?
                  and uc.terms_version = ?
                """,
            Integer.class,
            adminEmail,
            PRIVACY_POLICY_VERSION,
            TERMS_VERSION
        );
        assertThat(adminConsents).isOne();
    }

    @Test
    void registerWithoutConsentReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJsonWithoutConsent(
                    "missing-consent-" + UUID.randomUUID() + "@example.com",
                    "strong-password"
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());
    }

    @Test
    void registerRejectsOutdatedConsentVersion() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "outdated-%s@example.com",
                      "password": "strong-password",
                      "acceptedPrivacyPolicy": true,
                      "acceptedTerms": true,
                      "privacyPolicyVersion": "2020-01-01",
                      "termsVersion": "%s"
                    }
                    """.formatted(UUID.randomUUID(), TERMS_VERSION)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }
}
