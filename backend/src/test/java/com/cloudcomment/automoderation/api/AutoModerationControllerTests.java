package com.cloudcomment.automoderation.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.automoderation.application.AutoModerationFeedbackService;
import com.cloudcomment.automoderation.application.AutoModerationPolicyService;
import com.cloudcomment.automoderation.application.AutoModerationPolicySet;
import com.cloudcomment.automoderation.domain.AutoModerationExecutionMode;
import com.cloudcomment.automoderation.domain.AutoModerationFeedback;
import com.cloudcomment.automoderation.domain.AutoModerationFeedbackType;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyConfig;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyLifecycle;
import com.cloudcomment.automoderation.domain.AutoModerationPolicyVersion;
import com.cloudcomment.automoderation.domain.AutoModerationPreset;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.application.SiteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminRequest;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class AutoModerationControllerTests {

    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private AutoModerationPolicyService policyService;

    @MockitoBean
    private AutoModerationFeedbackService feedbackService;

    @MockitoBean
    private SiteService siteService;

    @Test
    void policiesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/sites/{siteId}/automoderation/policies", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(policyService);
    }

    @Test
    void listReturnsExactEnvelopeAndActiveMarker() throws Exception {
        AuthenticatedUser user = user();
        UUID siteId = UUID.randomUUID();
        AutoModerationPolicyVersion active = policy(siteId, 2, AutoModerationPolicyLifecycle.PUBLISHED);
        AutoModerationPolicyVersion draft = policy(siteId, null, AutoModerationPolicyLifecycle.DRAFT);
        when(currentUserService.getCurrentUser(eq("plain-session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN))).thenReturn(user);
        when(policyService.list(user, siteId)).thenReturn(new AutoModerationPolicySet(
            active, draft, List.of(active)
        ));

        mockMvc.perform(get("/api/sites/{siteId}/automoderation/policies", siteId)
                .with(adminRequest("plain-session-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activePolicy.id", is(active.id().toString())))
            .andExpect(jsonPath("$.activePolicy.state", is("PUBLISHED")))
            .andExpect(jsonPath("$.activePolicy.active", is(true)))
            .andExpect(jsonPath("$.activePolicy.reviewThreshold", is(45)))
            .andExpect(jsonPath("$.draft.state", is("DRAFT")))
            .andExpect(jsonPath("$.versions", hasSize(1)));
    }

    @Test
    void invalidDraftThresholdsAreRejectedBeforeService() throws Exception {
        AuthenticatedUser user = user();
        UUID siteId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN))).thenReturn(user);

        mockMvc.perform(patch("/api/sites/{siteId}/automoderation/policies/{policyId}", siteId, policyId)
                .with(adminRequest("plain-session-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "expectedRevision": 1,
                      "preset": "CUSTOM",
                      "reviewThreshold": 1001,
                      "spamThreshold": 1002
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));

        verifyNoInteractions(policyService);
    }

    @Test
    void feedbackIsOwnerScopedAndReturnsStableShape() throws Exception {
        AuthenticatedUser user = user();
        UUID commentId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN))).thenReturn(user);
        when(feedbackService.put(user, commentId, AutoModerationFeedbackType.FALSE_POSITIVE))
            .thenReturn(new AutoModerationFeedback(
                UUID.randomUUID(), commentId, policyId, user.id(),
                AutoModerationFeedbackType.FALSE_POSITIVE, NOW
            ));

        mockMvc.perform(put("/api/moderation/comments/{commentId}/automoderation-feedback", commentId)
                .with(adminRequest("plain-session-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FALSE_POSITIVE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("FALSE_POSITIVE")))
            .andExpect(jsonPath("$.createdAt", is("2026-07-13T10:00:00Z")));

        UUID foreignComment = UUID.randomUUID();
        when(feedbackService.put(user, foreignComment, AutoModerationFeedbackType.FALSE_POSITIVE))
            .thenThrow(new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));
        mockMvc.perform(put("/api/moderation/comments/{commentId}/automoderation-feedback", foreignComment)
                .with(adminRequest("plain-session-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FALSE_POSITIVE\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.message", is("Resource not found")));
    }

    private AutoModerationPolicyVersion policy(
        UUID siteId,
        Integer version,
        AutoModerationPolicyLifecycle lifecycle
    ) {
        return new AutoModerationPolicyVersion(
            UUID.randomUUID(), siteId, version, 1, lifecycle, true,
            AutoModerationPreset.BALANCED, AutoModerationExecutionMode.LIVE,
            AutoModerationPolicyConfig.preset(AutoModerationPreset.BALANCED),
            null, NOW, NOW, lifecycle == AutoModerationPolicyLifecycle.PUBLISHED ? NOW : null
        );
    }

    private AuthenticatedUser user() {
        UUID id = UUID.randomUUID();
        return new AuthenticatedUser(id, id + "@example.com", Set.of("OWNER"), NOW, NOW);
    }
}
