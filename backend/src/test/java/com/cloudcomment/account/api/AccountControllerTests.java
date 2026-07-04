package com.cloudcomment.account.api;

import com.cloudcomment.account.application.AccountDeletionConfirmationService;
import com.cloudcomment.account.application.AccountDeletionRequestService;
import com.cloudcomment.account.application.AccountDeletionRequestView;
import com.cloudcomment.account.application.PersonalDataExportService;
import com.cloudcomment.account.application.PersonalDataSnapshot;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class AccountControllerTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private AccountDeletionRequestService deletionRequestService;

    @MockitoBean
    private AccountDeletionConfirmationService deletionConfirmationService;

    @MockitoBean
    private PersonalDataExportService personalDataExportService;

    @Test
    void createDeletionRequestRequiresBearerAuthentication() throws Exception {
        mockMvc.perform(post("/api/account/deletion-requests"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        verifyNoInteractions(deletionRequestService);
    }

    @Test
    void createDeletionRequestReturnsPendingRequest() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID requestId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(deletionRequestService.createOrRefresh(currentUser)).thenReturn(
            new AccountDeletionRequestView(requestId, currentUser.id(), "PENDING", TIMESTAMP, TIMESTAMP.plusSeconds(3600))
        );

        mockMvc.perform(post("/api/account/deletion-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(requestId.toString())))
            .andExpect(jsonPath("$.userId", is(currentUser.id().toString())))
            .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void getCurrentDeletionRequestReturnsActiveRequest() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID requestId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(deletionRequestService.getCurrent(currentUser)).thenReturn(
            new AccountDeletionRequestView(requestId, currentUser.id(), "PENDING", TIMESTAMP, TIMESTAMP.plusSeconds(3600))
        );

        mockMvc.perform(get("/api/account/deletion-requests/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void exportPersonalDataReturnsCurrentUserSnapshot() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(personalDataExportService.export(currentUser)).thenReturn(new PersonalDataSnapshot(
            new PersonalDataSnapshot.AccountProfile(
                currentUser.id(),
                currentUser.email(),
                null,
                true,
                TIMESTAMP,
                TIMESTAMP,
                null
            ),
            currentUser.roles().stream().toList(),
            java.util.List.of(),
            new PersonalDataSnapshot.Sessions(1, 0, 0),
            new PersonalDataSnapshot.Resources(0, 0, 0, 0, 0, 0),
            null,
            TIMESTAMP
        ));

        mockMvc.perform(get("/api/account/personal-data")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.id", is(currentUser.id().toString())))
            .andExpect(jsonPath("$.account.email", is(currentUser.email())))
            .andExpect(jsonPath("$.sessions.active", is(1)));
    }

    @Test
    void exportPersonalDataRequiresBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/account/personal-data"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        verifyNoInteractions(personalDataExportService);
    }

    @Test
    void confirmDeletionIsPublicAndDoesNotRequireBearer() throws Exception {
        doNothing().when(deletionConfirmationService).confirm("confirmation-token");

        mockMvc.perform(post("/api/account/deletion-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "confirmation-token"
                    }
                    """))
            .andExpect(status().isNoContent());

        verify(deletionConfirmationService).confirm("confirmation-token");
        verifyNoInteractions(currentUserService);
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), TIMESTAMP, TIMESTAMP);
    }
}
