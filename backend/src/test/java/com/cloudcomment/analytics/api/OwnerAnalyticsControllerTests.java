package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.application.OwnerAnalyticsService;
import com.cloudcomment.analytics.domain.ActiveCommenter;
import com.cloudcomment.analytics.domain.AnalyticsSummary;
import com.cloudcomment.analytics.domain.CommentTimePoint;
import com.cloudcomment.analytics.domain.ModerationStatusCount;
import com.cloudcomment.analytics.domain.OwnerAnalytics;
import com.cloudcomment.analytics.domain.ReactionTypeCount;
import com.cloudcomment.analytics.domain.TopPageAnalytics;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class OwnerAnalyticsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private OwnerAnalyticsService ownerAnalyticsService;

    @Test
    void ownerAnalyticsRequiresBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/analytics/owner"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.path", is("/api/analytics/owner")));

        verifyNoInteractions(ownerAnalyticsService);
    }

    @Test
    void ownerAnalyticsReturnsOwnerScopedDashboard() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        OwnerAnalytics analytics = analytics(siteId);
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(ownerAnalyticsService.getOwnerAnalytics(currentUser, "30d", siteId)).thenReturn(analytics);

        mockMvc.perform(get("/api/analytics/owner")
                .param("siteId", siteId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.range", is("30d")))
            .andExpect(jsonPath("$.siteId", is(siteId.toString())))
            .andExpect(jsonPath("$.summary.sites", is(1)))
            .andExpect(jsonPath("$.summary.comments", is(12)))
            .andExpect(jsonPath("$.summary.replies", is(3)))
            .andExpect(jsonPath("$.summary.reactions", is(7)))
            .andExpect(jsonPath("$.commentsOverTime[0].bucket", is("2026-07-01")))
            .andExpect(jsonPath("$.commentsOverTime[0].total", is(5)))
            .andExpect(jsonPath("$.moderationFunnel[0].status", is("APPROVED")))
            .andExpect(jsonPath("$.reactionDistribution[0].type", is("LIKE")))
            .andExpect(jsonPath("$.topPages[0].pageUrl", is("https://example.com/post")))
            .andExpect(jsonPath("$.activeCommenters[0].email", is("author@example.com")));

        verify(ownerAnalyticsService).getOwnerAnalytics(currentUser, "30d", siteId);
    }

    @Test
    void ownerAnalyticsUsesDefaultRange() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(ownerAnalyticsService.getOwnerAnalytics(eq(currentUser), eq("30d"), isNull()))
            .thenReturn(analytics(null));

        mockMvc.perform(get("/api/analytics/owner")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.range", is("30d")));
    }

    @Test
    void ownerAnalyticsRejectsInvalidRange() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(get("/api/analytics/owner")
                .param("range", "365d")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/analytics/owner")));

        verifyNoInteractions(ownerAnalyticsService);
    }

    @Test
    void ownerAnalyticsMasksForeignSiteAsNotFound() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(ownerAnalyticsService.getOwnerAnalytics(currentUser, "7d", siteId))
            .thenThrow(new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));

        mockMvc.perform(get("/api/analytics/owner")
                .param("range", "7d")
                .param("siteId", siteId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    private OwnerAnalytics analytics(UUID siteId) {
        UUID pageId = UUID.randomUUID();
        UUID ownerSiteId = siteId != null ? siteId : UUID.randomUUID();
        return new OwnerAnalytics(
            "30d",
            siteId,
            Instant.parse("2026-06-06T00:00:00Z"),
            Instant.parse("2026-07-05T15:30:00Z"),
            new AnalyticsSummary(1, 2, 12, 3, 7, 2, 8, 1, 0, 1),
            List.of(new CommentTimePoint(LocalDate.parse("2026-07-01"), 5, 4, 1, 0)),
            List.of(new ModerationStatusCount("APPROVED", 8)),
            List.of(new ReactionTypeCount("LIKE", 7)),
            List.of(new TopPageAnalytics(
                pageId,
                ownerSiteId,
                "Example site",
                "https://example.com/post",
                10,
                2,
                7,
                8,
                1,
                1,
                Instant.parse("2026-07-04T12:00:00Z")
            )),
            List.of(new ActiveCommenter(
                UUID.randomUUID(),
                "author@example.com",
                "Author",
                6,
                5,
                1,
                0,
                Instant.parse("2026-07-04T12:00:00Z")
            ))
        );
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(
            UUID.randomUUID(),
            "owner@example.com",
            Set.of("OWNER"),
            Instant.parse("2026-06-23T12:00:00Z"),
            Instant.parse("2026-06-23T12:00:00Z")
        );
    }
}
