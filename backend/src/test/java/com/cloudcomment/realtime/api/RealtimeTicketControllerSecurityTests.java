package com.cloudcomment.realtime.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.auth.domain.SessionAudience;
import com.cloudcomment.realtime.application.RealtimeTicket;
import com.cloudcomment.realtime.application.RealtimeTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static com.cloudcomment.support.AdminSecurityTestSupport.adminRequest;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminSession;
import static com.cloudcomment.support.AdminSecurityTestSupport.csrf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class RealtimeTicketControllerSecurityTests {

    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private RealtimeTicketService ticketService;

    @Test
    void realtimeTicketRequiresAdminCookieAndCsrf() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
            UUID.randomUUID(),
            "owner@example.com",
            Set.of("OWNER"),
            NOW,
            NOW
        );
        when(currentUserService.getCurrentUser("admin-token", SessionAudience.ADMIN)).thenReturn(user);
        when(ticketService.issue(user)).thenReturn(new RealtimeTicket("ticket-value", NOW.plusSeconds(60)));

        mockMvc.perform(post("/api/realtime/tickets")
                .with(adminSession("admin-token")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", is("INVALID_CSRF_TOKEN")));

        mockMvc.perform(post("/api/realtime/tickets")
                .with(csrf())
                .header("Authorization", "Bearer admin-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        mockMvc.perform(post("/api/realtime/tickets")
                .with(adminRequest("admin-token")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ticket", is("ticket-value")));
    }

    @Test
    void realtimeTicketDoesNotReachServiceWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/realtime/tickets")
                .with(adminSession("admin-token")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(ticketService);
    }
}
