package com.cloudcomment.auth.api;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.auth.application.LoginResult;
import com.cloudcomment.auth.application.LoginService;
import com.cloudcomment.auth.application.LogoutService;
import com.cloudcomment.auth.application.RegisteredUser;
import com.cloudcomment.auth.application.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private LogoutService logoutService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    void registerCreatesUserWithoutPasswordInResponse() throws Exception {
        UUID id = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-06-23T12:00:00Z");
        when(registrationService.register(eq("User@Example.com"), eq("strong-password")))
            .thenReturn(new RegisteredUser(id, "user@example.com", Set.of("COMMENTER"), timestamp, timestamp));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "User@Example.com",
                      "password": "strong-password"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(id.toString())))
            .andExpect(jsonPath("$.email", is("user@example.com")))
            .andExpect(jsonPath("$.roles", containsInAnyOrder("COMMENTER")))
            .andExpect(jsonPath("$.createdAt", is("2026-06-23T12:00:00Z")))
            .andExpect(jsonPath("$.updatedAt", is("2026-06-23T12:00:00Z")))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-email",
                      "password": "short"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());
    }

    @Test
    void registerReturnsConflictWhenEmailAlreadyUsed() throws Exception {
        when(registrationService.register(eq("used@example.com"), eq("strong-password")))
            .thenThrow(new ApplicationException(ApiErrorCode.EMAIL_ALREADY_USED, "Email is already used"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "used@example.com",
                      "password": "strong-password"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_USED")))
            .andExpect(jsonPath("$.error.message", is("Email is already used")))
            .andExpect(jsonPath("$.error.status", is(409)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/register")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void loginReturnsTokenAndUserWithoutPasswordInResponse() throws Exception {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-23T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-23T13:00:00Z");
        Instant expiresAt = Instant.parse("2026-06-30T12:00:00Z");
        AuthenticatedUser user = new AuthenticatedUser(
            id,
            "user@example.com",
            Set.of("COMMENTER"),
            createdAt,
            updatedAt
        );
        when(loginService.login(eq("User@Example.com"), eq("strong-password")))
            .thenReturn(new LoginResult("plain-session-token", "Bearer", expiresAt, user));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "User@Example.com",
                      "password": "strong-password"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", is("plain-session-token")))
            .andExpect(jsonPath("$.tokenType", is("Bearer")))
            .andExpect(jsonPath("$.expiresAt", is("2026-06-30T12:00:00Z")))
            .andExpect(jsonPath("$.user.id", is(id.toString())))
            .andExpect(jsonPath("$.user.email", is("user@example.com")))
            .andExpect(jsonPath("$.user.roles", containsInAnyOrder("COMMENTER")))
            .andExpect(jsonPath("$.user.createdAt", is("2026-06-23T12:00:00Z")))
            .andExpect(jsonPath("$.user.updatedAt", is("2026-06-23T13:00:00Z")))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.user.password").doesNotExist())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void loginRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-email",
                      "password": "short"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() throws Exception {
        when(loginService.login(eq("user@example.com"), eq("wrong-password")))
            .thenThrow(new ApplicationException(
                ApiErrorCode.INVALID_CREDENTIALS,
                "Invalid email or password"
            ));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "user@example.com",
                      "password": "wrong-password"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")))
            .andExpect(jsonPath("$.error.message", is("Invalid email or password")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/login")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void meReturnsCurrentUserWithoutPasswordInResponse() throws Exception {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-23T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-23T13:00:00Z");
        when(currentUserService.getCurrentUser(eq("plain-session-token")))
            .thenReturn(new AuthenticatedUser(
                id,
                "user@example.com",
                Set.of("COMMENTER"),
                createdAt,
                updatedAt
            ));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(id.toString())))
            .andExpect(jsonPath("$.email", is("user@example.com")))
            .andExpect(jsonPath("$.roles", containsInAnyOrder("COMMENTER")))
            .andExpect(jsonPath("$.createdAt", is("2026-06-23T12:00:00Z")))
            .andExpect(jsonPath("$.updatedAt", is("2026-06-23T13:00:00Z")))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(currentUserService).getCurrentUser("plain-session-token");
    }

    @Test
    void meAcceptsBearerSchemeCaseInsensitively() throws Exception {
        UUID id = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-06-23T12:00:00Z");
        when(currentUserService.getCurrentUser(eq("plain-session-token")))
            .thenReturn(new AuthenticatedUser(id, "user@example.com", Set.of("COMMENTER"), timestamp, timestamp));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "bearer plain-session-token"))
            .andExpect(status().isOk());

        verify(currentUserService).getCurrentUser("plain-session-token");
    }

    @Test
    void meReturnsUnauthorizedWhenAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/me")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void meReturnsUnauthorizedWhenAuthorizationHeaderIsMalformed() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Basic plain-session-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/me")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void meReturnsUnauthorizedWhenSessionIsInvalidOrExpired() throws Exception {
        when(currentUserService.getCurrentUser(eq("expired-session-token")))
            .thenThrow(new ApplicationException(
                ApiErrorCode.INVALID_SESSION,
                "Invalid or expired session"
            ));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer expired-session-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/me")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void logoutRevokesCurrentSession() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer plain-session-token"))
            .andExpect(status().isNoContent());

        verify(logoutService).logout("plain-session-token");
    }

    @Test
    void logoutTreatsAlreadyRevokedSessionAsSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer already-revoked-token"))
            .andExpect(status().isNoContent());

        verify(logoutService).logout("already-revoked-token");
    }

    @Test
    void logoutAcceptsBearerSchemeCaseInsensitively() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "bearer plain-session-token"))
            .andExpect(status().isNoContent());

        verify(logoutService).logout("plain-session-token");
    }

    @Test
    void logoutReturnsUnauthorizedWhenAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/logout")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void logoutReturnsUnauthorizedWhenAuthorizationHeaderIsMalformed() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Basic plain-session-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/logout")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void logoutReturnsUnauthorizedWhenSessionIsInvalidOrExpired() throws Exception {
        doThrow(new ApplicationException(
            ApiErrorCode.INVALID_SESSION,
            "Invalid or expired session"
        )).when(logoutService).logout(eq("expired-session-token"));

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer expired-session-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/logout")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }
}
