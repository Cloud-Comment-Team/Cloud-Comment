package com.cloudcomment.api;

import com.cloudcomment.api.auth.AuthController;
import com.cloudcomment.api.error.ApiErrorCode;
import com.cloudcomment.api.error.ApiException;
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
}
