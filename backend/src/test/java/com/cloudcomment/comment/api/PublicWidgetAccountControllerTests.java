package com.cloudcomment.comment.api;

import com.cloudcomment.account.application.AccountDeletionRequestService;
import com.cloudcomment.account.application.PersonalDataExportService;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.widgetcontext.application.ResolvedWidgetContext;
import com.cloudcomment.widgetcontext.application.WidgetContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "cloud-comment.embed.api-base-url=https://api.example.net/api",
    "cloud-comment.embed.widget-base-url=https://widget.example.net"
})
@AutoConfigureMockMvc
class PublicWidgetAccountControllerTests {

    private static final String FRAME_ORIGIN = "https://widget.example.net";
    private static final String EMBEDDING_ORIGIN = "https://example.com";
    private static final String CONTEXT_TOKEN = "frame-context-token";

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

    @MockitoBean
    private WidgetContextService widgetContextService;

    @BeforeEach
    void setUpWidgetContext() {
        when(widgetContextService.acceptsFrameOrigin(FRAME_ORIGIN)).thenReturn(true);
        when(widgetContextService.resolve(any(UUID.class), eq(CONTEXT_TOKEN))).thenAnswer(invocation ->
            new ResolvedWidgetContext(
                UUID.fromString("3a32fc9d-c91d-48d9-a72f-c94354c3a078"),
                invocation.getArgument(0),
                EMBEDDING_ORIGIN,
                "a".repeat(64),
                Instant.parse("2026-07-13T14:00:00Z")
            )
        );
    }

    @Test
    void widgetCannotExportGlobalPersonalData() throws Exception {
        assertRemovedCapability(get("/api/public/sites/{siteId}/account/personal-data", UUID.randomUUID()));
    }

    @Test
    void widgetCannotRequestGlobalAccountDeletion() throws Exception {
        assertRemovedCapability(post("/api/public/sites/{siteId}/account/deletion-requests", UUID.randomUUID()));
    }

    private void assertRemovedCapability(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer scoped-widget-session"))
            .andExpect(status().isNotFound())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        verifyNoInteractions(currentUserService, deletionRequestService, personalDataExportService);
    }
}
