package com.cloudcomment.api;

import com.cloudcomment.api.auth.AuthController;
import com.cloudcomment.api.error.ApiErrorCode;
import com.cloudcomment.api.error.ApiException;
import com.cloudcomment.service.AuthenticatedUser;
import com.cloudcomment.service.LoginResult;
import com.cloudcomment.service.LoginService;
import com.cloudcomment.service.RegisteredUser;
import com.cloudcomment.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private LoginService loginService;

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
            .thenThrow(new ApiException(ApiErrorCode.EMAIL_ALREADY_USED, HttpStatus.CONFLICT, "Email is already used"));

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
            .thenThrow(new ApiException(
                ApiErrorCode.INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED,
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
}
