package com.cloudcomment.comment.api;

import com.cloudcomment.account.application.AccountDeletionRequestService;
import com.cloudcomment.account.application.AccountDeletionRequestView;
import com.cloudcomment.account.application.PersonalDataExportService;
import com.cloudcomment.account.application.PersonalDataSnapshot;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.comment.application.WidgetSiteAccess;
import com.cloudcomment.site.domain.ModerationMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class PublicWidgetAccountControllerTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");
    private static final String ORIGIN = "https://example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private DomainPolicyService domainPolicyService;

    @MockitoBean
    private AccountDeletionRequestService deletionRequestService;

    @MockitoBean
    private PersonalDataExportService personalDataExportService;

    @Test
    void widgetPersonalDataExportReturnsSnapshotForAuthenticatedCommenter() throws Exception {
        UUID siteId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        PersonalDataSnapshot snapshot = new PersonalDataSnapshot(
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
            List.of(),
            new PersonalDataSnapshot.Sessions(1, 0, 0),
            new PersonalDataSnapshot.Resources(0, 0, 0, 1, 0),
            null,
            TIMESTAMP
        );
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(domainPolicyService.validate(siteId, ORIGIN))
            .thenReturn(new WidgetSiteAccess(siteId, ModerationMode.PRE_MODERATION, ORIGIN));
        when(currentUserService.getCurrentUser("plain-session-token")).thenReturn(currentUser);
        when(personalDataExportService.export(currentUser)).thenReturn(snapshot);

        mockMvc.perform(get("/api/public/sites/{siteId}/account/personal-data", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(jsonPath("$.account.id", is(currentUser.id().toString())))
            .andExpect(jsonPath("$.account.email", is(currentUser.email())))
            .andExpect(jsonPath("$.resources.authoredComments", is(1)));

        verify(personalDataExportService).export(currentUser);
    }

    @Test
    void widgetDeletionRequestCreatesRequestForAuthenticatedCommenter() throws Exception {
        UUID siteId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        UUID requestId = UUID.randomUUID();
        AccountDeletionRequestView view = new AccountDeletionRequestView(
            requestId,
            currentUser.id(),
            "PENDING",
            TIMESTAMP,
            TIMESTAMP.plusSeconds(3600)
        );
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(domainPolicyService.validate(siteId, ORIGIN))
            .thenReturn(new WidgetSiteAccess(siteId, ModerationMode.PRE_MODERATION, ORIGIN));
        when(currentUserService.getCurrentUser("plain-session-token")).thenReturn(currentUser);
        when(deletionRequestService.createOrRefresh(currentUser)).thenReturn(view);

        mockMvc.perform(post("/api/public/sites/{siteId}/account/deletion-requests", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(jsonPath("$.id", is(requestId.toString())))
            .andExpect(jsonPath("$.userId", is(currentUser.id().toString())))
            .andExpect(jsonPath("$.status", is("PENDING")));

        verify(domainPolicyService).validate(siteId, ORIGIN);
        verify(deletionRequestService).createOrRefresh(currentUser);
    }

    @Test
    void widgetDeletionRequestRequiresBearerAuthentication() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(post("/api/public/sites/{siteId}/account/deletion-requests", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        verifyNoInteractions(deletionRequestService);
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(
            UUID.randomUUID(),
            "visitor@example.com",
            Set.of("COMMENTER"),
            TIMESTAMP,
            TIMESTAMP
        );
    }
}
