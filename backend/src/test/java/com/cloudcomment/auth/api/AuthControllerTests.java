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
import com.cloudcomment.privacy.application.ConsentTestSupport;
import com.cloudcomment.privacy.application.RegistrationConsent;
import com.cloudcomment.privacy.domain.ConsentSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminRequest;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminSession;
import static com.cloudcomment.support.AdminSecurityTestSupport.csrf;

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
        when(registrationService.register(
            eq("User@Example.com"),
            eq("strong-password"),
            eq(null),
            any(RegistrationConsent.class),
            eq(ConsentSource.ADMIN)
        )).thenReturn(new RegisteredUser(id, "user@example.com", Set.of("COMMENTER"), timestamp, timestamp));

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ConsentTestSupport.registerRequestJson("User@Example.com", "strong-password")))
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
                .with(csrf())
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
        when(registrationService.register(
            eq("used@example.com"),
            eq("strong-password"),
            eq(null),
            any(RegistrationConsent.class),
            eq(ConsentSource.ADMIN)
        )).thenThrow(new ApplicationException(ApiErrorCode.EMAIL_ALREADY_USED, "Email is already used"));

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ConsentTestSupport.registerRequestJson("used@example.com", "strong-password")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_USED")))
            .andExpect(jsonPath("$.error.message", is("Email is already used")))
            .andExpect(jsonPath("$.error.status", is(409)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/register")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void loginReturnsAdminCookieAndUserWithoutExposingToken() throws Exception {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-23T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-23T13:00:00Z");
        Instant expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        AuthenticatedUser user = new AuthenticatedUser(
            id,
            "user@example.com",
            Set.of("COMMENTER"),
            createdAt,
            updatedAt
        );
        when(loginService.loginReplacing(
            eq("User@Example.com"),
            eq("strong-password"),
            eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN),
            eq(null)
        ))
            .thenReturn(new LoginResult("plain-session-token", "Bearer", expiresAt, user));

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "User@Example.com",
                      "password": "strong-password"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", containsString("cloud_comment_admin_session=plain-session-token")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
            .andExpect(header().string("Set-Cookie", containsString("Path=/")))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=")))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.tokenType").doesNotExist())
            .andExpect(jsonPath("$.expiresAt", is(expiresAt.toString())))
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
                .with(csrf())
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
        when(loginService.loginReplacing(
            eq("user@example.com"),
            eq("wrong-password"),
            eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN),
            eq(null)
        ))
            .thenThrow(new ApplicationException(
                ApiErrorCode.INVALID_CREDENTIALS,
                "Invalid email or password"
            ));

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
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
        when(currentUserService.getCurrentUser(
            eq("plain-session-token"),
            eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)
        ))
            .thenReturn(new AuthenticatedUser(
                id,
                "user@example.com",
                Set.of("COMMENTER"),
                createdAt,
                updatedAt
            ));

        mockMvc.perform(get("/api/auth/me")
                .with(adminSession("plain-session-token")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.id", is(id.toString())))
            .andExpect(jsonPath("$.email", is("user@example.com")))
            .andExpect(jsonPath("$.roles", containsInAnyOrder("COMMENTER")))
            .andExpect(jsonPath("$.createdAt", is("2026-06-23T12:00:00Z")))
            .andExpect(jsonPath("$.updatedAt", is("2026-06-23T13:00:00Z")))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(currentUserService).getCurrentUser(
            "plain-session-token",
            com.cloudcomment.auth.domain.SessionAudience.ADMIN
        );
    }

    @Test
    void meRejectsAdminBearerAfterCookieCutover() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer plain-session-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        verify(currentUserService, never()).getCurrentUser(
            "plain-session-token",
            com.cloudcomment.auth.domain.SessionAudience.ADMIN
        );
    }

    @Test
    void meReturnsUnauthorizedWhenSessionCookieIsMissing() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/auth/me")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void meReturnsUnauthorizedWhenSessionIsInvalidOrExpired() throws Exception {
        when(currentUserService.getCurrentUser(
            eq("expired-session-token"),
            eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)
        ))
            .thenThrow(new ApplicationException(
                ApiErrorCode.INVALID_SESSION,
                "Invalid or expired session"
            ));

        mockMvc.perform(get("/api/auth/me")
                .with(adminSession("expired-session-token")))
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
                .with(adminRequest("plain-session-token")))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", containsString("cloud_comment_admin_session=")))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(logoutService).logoutIfPresent(
            "plain-session-token",
            com.cloudcomment.auth.domain.SessionAudience.ADMIN
        );
    }

    @Test
    void logoutIsIdempotentForRepeatedRequests() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .with(adminRequest("already-revoked-token")))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/logout")
                .with(adminRequest("already-revoked-token")))
            .andExpect(status().isNoContent());

        verify(logoutService, times(2)).logoutIfPresent(
            "already-revoked-token",
            com.cloudcomment.auth.domain.SessionAudience.ADMIN
        );
    }

    @Test
    void logoutWithoutSessionStillClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .with(csrf()))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(logoutService, never()).logoutIfPresent(
            any(),
            eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)
        );
    }

    @Test
    void logoutClearsCookieEvenWhenRevocationFailsUnexpectedly() throws Exception {
        doThrow(new IllegalStateException("database unavailable"))
            .when(logoutService)
            .logoutIfPresent("plain-session-token", com.cloudcomment.auth.domain.SessionAudience.ADMIN);

        mockMvc.perform(post("/api/auth/logout")
                .with(adminRequest("plain-session-token")))
            .andExpect(status().isInternalServerError())
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void unsafeAdminRequestRejectsMissingAndWrongCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", is("INVALID_CSRF_TOKEN")))
            .andExpect(jsonPath("$.error.status", is(403)));

        mockMvc.perform(post("/api/auth/logout")
                .with(adminSession("plain-session-token"))
                .header("X-CSRF-Token", "wrong"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", is("INVALID_CSRF_TOKEN")));
    }

    @Test
    void csrfEndpointIsPublicNoStoreAndIssuesHttpOnlyCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(header().string("Set-Cookie", containsString("cloud_comment_admin_csrf=")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
            .andExpect(jsonPath("$.headerName", is("X-CSRF-Token")))
            .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
