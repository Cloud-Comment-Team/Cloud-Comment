package com.cloudcomment.account.api;

import com.cloudcomment.shared.mail.LoggingMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AccountDeletionIntegrationTests {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("Confirm deletion by submitting this one-time token:\\s*(\\S+)");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LoggingMailSender loggingMailSender;

    @Test
    void accountDeletionFlowRevokesSessionsAndBlocksAuth() throws Exception {
        String email = "delete-" + UUID.randomUUID() + "@example.com";
        String password = "strong-password";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String sessionToken = extractJsonField(loginResponse, "token");
        String userId = extractJsonField(loginResponse, "user", "id");

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("PENDING")));

        mockMvc.perform(get("/api/account/deletion-requests/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PENDING")));

        String confirmationToken = extractConfirmationToken(loggingMailSender.lastSentMessage().textBody());

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isCreated());

        String rotatedToken = extractConfirmationToken(loggingMailSender.lastSentMessage().textBody());
        assertThat(rotatedToken).isNotEqualTo(confirmationToken);

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(rotatedToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));

        Boolean deleted = jdbcTemplate.queryForObject(
            "select deleted_at is not null from app_users where id = ?",
            Boolean.class,
            UUID.fromString(userId)
        );
        assertThat(deleted).isTrue();

        Integer activeSessions = jdbcTemplate.queryForObject(
            """
                select count(*)
                from auth_sessions
                where user_id = ?
                  and revoked_at is null
                """,
            Integer.class,
            UUID.fromString(userId)
        );
        assertThat(activeSessions).isZero();
    }

    @Test
    void confirmRejectsExpiredAndReusedTokens() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "strong-password"
                    }
                    """.formatted(email)))
            .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "strong-password"
                    }
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String sessionToken = extractJsonField(loginResponse, "token");

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
            .andExpect(status().isCreated());

        String token = extractConfirmationToken(loggingMailSender.lastSentMessage().textBody());
        jdbcTemplate.update(
            """
                update account_deletion_requests
                set expires_at = now() - interval '1 minute'
                where token_hash is not null
                """
        );

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(token)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("BUSINESS_ERROR")));

        jdbcTemplate.update(
            """
                update account_deletion_requests
                set expires_at = now() + interval '1 hour',
                    confirmed_at = null
                where token_hash is not null
                """
        );

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(token)))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "%s"
                    }
                    """.formatted(token)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("BUSINESS_ERROR")));

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "unknown-token-value"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    private String extractConfirmationToken(String body) {
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String extractJsonField(String json, String objectName, String field) {
        Pattern pattern = Pattern.compile("\"" + objectName + "\":\\{\"id\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
