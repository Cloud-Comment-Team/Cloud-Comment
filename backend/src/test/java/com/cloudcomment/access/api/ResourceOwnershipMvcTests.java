package com.cloudcomment.access.api;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.shared.web.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class ResourceOwnershipMvcTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ResourceOwnershipService resourceOwnershipService;

    @Test
    void protectedEndpointAllowsOwnedResource() throws Exception {
        UUID siteId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(get("/api/test/sites/{siteId}/ownership", siteId)
                .header("Authorization", "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("owned")));

        verify(resourceOwnershipService).assertSiteOwnedBy(currentUser, siteId);
    }

    @Test
    void protectedEndpointReturnsNotFoundForForeignOrMissingResource() throws Exception {
        UUID siteId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        doThrow(new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"))
            .when(resourceOwnershipService)
            .assertSiteOwnedBy(currentUser, siteId);

        mockMvc.perform(get("/api/test/sites/{siteId}/ownership", siteId)
                .header("Authorization", "Bearer plain-session-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")))
            .andExpect(jsonPath("$.error.status", is(404)))
            .andExpect(jsonPath("$.error.path", is("/api/test/sites/" + siteId + "/ownership")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void protectedEndpointStillRequiresBearerTokenBeforeOwnershipCheck() throws Exception {
        UUID siteId = UUID.randomUUID();

        mockMvc.perform(get("/api/test/sites/{siteId}/ownership", siteId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/test/sites/" + siteId + "/ownership")))
            .andExpect(jsonPath("$.error.fields", empty()));

        verifyNoInteractions(resourceOwnershipService);
    }

    private AuthenticatedUser currentUser() {
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-06-25T00:00:00Z");
        return new AuthenticatedUser(userId, "owner@example.com", Set.of("OWNER"), timestamp, timestamp);
    }

    @TestConfiguration
    static class TestControllerConfiguration {

        @Bean
        TestOwnershipController testOwnershipController(ResourceOwnershipService resourceOwnershipService) {
            return new TestOwnershipController(resourceOwnershipService);
        }
    }

    @RestController
    @RequestMapping("/api/test/sites")
    static class TestOwnershipController {

        private final ResourceOwnershipService resourceOwnershipService;

        TestOwnershipController(ResourceOwnershipService resourceOwnershipService) {
            this.resourceOwnershipService = resourceOwnershipService;
        }

        @GetMapping("/{siteId}/ownership")
        Map<String, String> ownership(@CurrentUser AuthenticatedUser currentUser, @PathVariable UUID siteId) {
            resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
            return Map.of("status", "owned");
        }
    }
}
