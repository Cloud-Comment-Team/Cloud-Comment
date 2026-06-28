package com.cloudcomment.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance smoke tests for implemented MVP endpoints.
 * Checklist reference: docs/mvp-qa-contracts.md
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MvpApiSmokeTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mvpHealthAndAuthSmokeChecklist() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("UP")))
            .andExpect(jsonPath("$.application", is("cloud-comment")));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-an-email",
                      "password": "short"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/auth/register")))
            .andExpect(jsonPath("$.error.fields").isArray());

        String email = "smoke-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "strong-password"
                    }
                    """.formatted(email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email", is(email)))
            .andExpect(jsonPath("$.roles", contains("COMMENTER")));

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "strong-password"
                    }
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType", is("Bearer")))
            .andExpect(jsonPath("$.user.email", is(email)))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String token = extractToken(loginResponse);
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is(email)));

        String siteResponse = mockMvc.perform(post("/api/sites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Smoke site",
                      "domain": "smoke-%s.example.com",
                      "moderationMode": "PRE_MODERATION",
                      "allowedOrigins": ["https://smoke-%s.example.com"]
                    }
                    """.formatted(email.hashCode(), email.hashCode())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is("Smoke site")))
            .andExpect(jsonPath("$.moderationMode", is("PRE_MODERATION")))
            .andExpect(jsonPath("$.isActive", is(true)))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String siteId = extractString(siteResponse, "id");
        String publicKey = extractString(siteResponse, "publicKey");
        assertThat(siteId).isNotBlank();
        assertThat(publicKey).matches("[0-9a-f]{64}");

        mockMvc.perform(get("/api/sites/{siteId}/embed-code", siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.siteId", is(siteId)))
            .andExpect(jsonPath("$.scriptUrl", is("http://localhost/widget/cloud-comment-widget.js")))
            .andExpect(jsonPath("$.embedCode", is("<script src=\"http://localhost/widget/cloud-comment-widget.js\" data-site-id=\"" + siteId + "\" data-api-base-url=\"http://localhost/api\"></script>")))
            .andExpect(jsonPath("$.dataAttributes.siteId", is(siteId)))
            .andExpect(jsonPath("$.dataAttributes.apiBaseUrl", is("http://localhost/api")));

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.path", is("/api/auth/me")))
            .andExpect(jsonPath("$.error.fields", empty()));

        mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));
    }

    private String extractToken(String json) {
        return extractString(json, "token");
    }

    private String extractString(String json, String fieldName) {
        Matcher matcher = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
