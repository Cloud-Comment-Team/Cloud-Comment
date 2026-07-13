package com.cloudcomment.shared.web.security;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminSession;
import static com.cloudcomment.support.AdminSecurityTestSupport.csrf;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class ApiAuthenticationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    void protectedEndpointRejectsMissingAdminCookie() throws Exception {
        mockMvc.perform(get("/api/protected-test/current"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/protected-test/current")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void protectedEndpointRejectsBearerToken() throws Exception {
        mockMvc.perform(get("/api/protected-test/current")
                .header("Authorization", "Basic plain-session-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/protected-test/current")))
            .andExpect(jsonPath("$.error.fields", empty()));

        verify(currentUserService, never()).getCurrentUser(
            "plain-session-token",
            com.cloudcomment.auth.domain.SessionAudience.ADMIN
        );
    }

    @Test
    void protectedEndpointRejectsMissingBearerTokenBeforeMethodMismatchHandling() throws Exception {
        mockMvc.perform(patch("/api/protected-test/current"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", is("INVALID_CSRF_TOKEN")));

        mockMvc.perform(patch("/api/protected-test/current").with(csrf()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/protected-test/current")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void protectedEndpointRejectsInvalidOrExpiredSession() throws Exception {
        when(currentUserService.getCurrentUser(
            eq("expired-session-token"),
            eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)
        ))
            .thenThrow(new ApplicationException(
                ApiErrorCode.INVALID_SESSION,
                "Invalid or expired session"
            ));

        mockMvc.perform(get("/api/protected-test/current")
                .with(adminSession("expired-session-token")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/protected-test/current")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void protectedEndpointReceivesAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-06-23T12:00:00Z");
        when(currentUserService.getCurrentUser(
            eq("plain-session-token"),
            eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)
        ))
            .thenReturn(new AuthenticatedUser(userId, "user@example.com", Set.of("COMMENTER"), timestamp, timestamp));

        mockMvc.perform(get("/api/protected-test/current")
                .with(adminSession("plain-session-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(userId.toString())))
            .andExpect(jsonPath("$.email", is("user@example.com")));

        verify(currentUserService).getCurrentUser(
            "plain-session-token",
            com.cloudcomment.auth.domain.SessionAudience.ADMIN
        );
    }

    @TestConfiguration
    static class TestControllerConfiguration {

        @Bean
        TestProtectedController testProtectedController() {
            return new TestProtectedController();
        }
    }

    @RestController
    @RequestMapping("/api/protected-test")
    static class TestProtectedController {

        @GetMapping("/current")
        Map<String, String> current(@CurrentUser AuthenticatedUser currentUser) {
            return Map.of(
                "id", currentUser.id().toString(),
                "email", currentUser.email()
            );
        }
    }
}
