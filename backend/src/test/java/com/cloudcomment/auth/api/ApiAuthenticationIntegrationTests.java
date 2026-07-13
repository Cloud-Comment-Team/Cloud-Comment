package com.cloudcomment.auth.api;

import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.auth.application.SessionTokenHasher;
import com.cloudcomment.auth.domain.SessionAudience;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockCookie;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.cloudcomment.privacy.application.ConsentTestSupport.registerRequestJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminRequest;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminSession;
import static com.cloudcomment.support.AdminSecurityTestSupport.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApiAuthenticationIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SessionTokenHasher sessionTokenHasher;

    @Test
    void adminCookieCanAccessCurrentUserAndBearerIsRejected() throws Exception {
        String email = "auth-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(email, "strong-password")))
            .andExpect(status().isCreated());

        String setCookie = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
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
            .getHeader(HttpHeaders.SET_COOKIE);
        String token = MockCookie.parse(setCookie).getValue();
        UUID userId = jdbcTemplate.queryForObject(
            "select id from app_users where email = ?",
            UUID.class,
            email
        );
        String origin = "https://audience.example.com";
        UUID siteId = jdbcTemplate.queryForObject(
            "insert into sites (owner_id, name, domain, public_key) values (?, 'Audience', ?, ?) returning id",
            UUID.class,
            userId,
            "audience-" + UUID.randomUUID() + ".example.com",
            UUID.randomUUID().toString().replace("-", "").repeat(2).substring(0, 64)
        );
        jdbcTemplate.update(
            "insert into site_allowed_origins (site_id, origin) values (?, ?)",
            siteId,
            origin
        );
        String widgetToken = "widget-" + UUID.randomUUID();
        userAccountRepository.createSession(
            userId,
            sessionTokenHasher.hash(widgetToken),
            SessionAudience.WIDGET,
            Instant.now().plus(Duration.ofHours(1))
        );
        String legacyToken = "legacy-" + UUID.randomUUID();
        jdbcTemplate.update(
            "insert into auth_sessions (user_id, token_hash, expires_at) values (?, ?, now() + interval '1 hour')",
            userId,
            sessionTokenHasher.hash(legacyToken)
        );

        mockMvc.perform(get("/api/auth/me")
                .with(adminSession(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is(email)))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/public/sites/{siteId}/auth/me", siteId)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + widgetToken))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.email", is(email)));

        mockMvc.perform(get("/api/auth/me")
                .with(adminSession(widgetToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        mockMvc.perform(get("/api/public/sites/{siteId}/auth/me", siteId)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        mockMvc.perform(get("/api/auth/me")
                .with(adminSession(legacyToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        mockMvc.perform(get("/api/public/sites/{siteId}/auth/me", siteId)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + legacyToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.fields", empty()));

        mockMvc.perform(post("/api/auth/logout")
                .with(adminRequest(token)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                .with(adminSession(token)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.fields", empty()));

        String expiredToken = "expired-" + UUID.randomUUID();
        var expiredUser = userAccountRepository.create(
            "expired-" + UUID.randomUUID() + "@example.com",
            "hashed-password",
            Set.of("COMMENTER")
        );
        Instant expiresAt = Instant.now().minus(Duration.ofHours(1));
        Instant createdAt = expiresAt.minus(Duration.ofDays(1));
        jdbcTemplate.update(
            """
                insert into auth_sessions (user_id, token_hash, audience, created_at, expires_at)
                values (?, ?, 'ADMIN', ?, ?)
                """,
            expiredUser.id(),
            sessionTokenHasher.hash(expiredToken),
            OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        );

        mockMvc.perform(get("/api/auth/me")
                .with(adminSession(expiredToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void reloginRotatesAdminCookieAndRevokesPreviousSession() throws Exception {
        String email = "rotate-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(email, "strong-password")))
            .andExpect(status().isCreated());

        String firstCookie = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.SET_COOKIE);
        String firstToken = MockCookie.parse(firstCookie).getValue();

        String secondCookie = mockMvc.perform(post("/api/auth/login")
                .with(adminRequest(firstToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.SET_COOKIE);
        String secondToken = MockCookie.parse(secondCookie).getValue();

        assertThat(secondToken).isNotEqualTo(firstToken);
        mockMvc.perform(get("/api/auth/me").with(adminSession(firstToken)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").with(adminSession(secondToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is(email)));

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from auth_sessions where audience = 'ADMIN' and revoked_at is null and token_hash = ?",
            Integer.class,
            sessionTokenHasher.hash(secondToken)
        )).isOne();
    }

    private String loginJson(String email) {
        return """
            {
              "email": "%s",
              "password": "strong-password"
            }
            """.formatted(email);
    }

}
