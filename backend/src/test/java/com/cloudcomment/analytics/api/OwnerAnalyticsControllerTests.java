package com.cloudcomment.analytics.api;

import com.cloudcomment.analytics.application.OwnerAnalyticsService;
import com.cloudcomment.analytics.domain.ActiveCommenter;
import com.cloudcomment.analytics.domain.AnalyticsBucket;
import com.cloudcomment.analytics.domain.AnalyticsComparison;
import com.cloudcomment.analytics.domain.AnalyticsSummary;
import com.cloudcomment.analytics.domain.AnalyticsWorkload;
import com.cloudcomment.analytics.domain.CommentTimePoint;
import com.cloudcomment.analytics.domain.ModerationStatusCount;
import com.cloudcomment.analytics.domain.OwnerAnalytics;
import com.cloudcomment.analytics.domain.PeriodActivity;
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
        when(ownerAnalyticsService.getOwnerAnalytics(currentUser, "30d", siteId, "UTC")).thenReturn(analytics);

        mockMvc.perform(get("/api/analytics/owner")
                .param("siteId", siteId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.range", is("30d")))
            .andExpect(jsonPath("$.siteId", is(siteId.toString())))
            .andExpect(jsonPath("$.timeZone", is("UTC")))
            .andExpect(jsonPath("$.bucketGranularity", is("DAY")))
            .andExpect(jsonPath("$.summary.sites", is(1)))
            .andExpect(jsonPath("$.summary.comments", is(12)))
            .andExpect(jsonPath("$.summary.replies", is(3)))
            .andExpect(jsonPath("$.summary.reactions", is(7)))
            .andExpect(jsonPath("$.commentsOverTime[0].bucket", is("2026-07-01")))
            .andExpect(jsonPath("$.commentsOverTime[0].total", is(5)))
            .andExpect(jsonPath("$.comparison.comments.current", is(12)))
            .andExpect(jsonPath("$.comparison.comments.previous", is(10)))
            .andExpect(jsonPath("$.comparison.comments.percentageChange", is(20.0)))
            .andExpect(jsonPath("$.workload.requiringDecision", is(3)))
            .andExpect(jsonPath("$.moderationDistribution[0].status", is("APPROVED")))
            .andExpect(jsonPath("$.moderationFunnel[0].status", is("APPROVED")))
            .andExpect(jsonPath("$.reactionDistribution[0].type", is("LIKE")))
            .andExpect(jsonPath("$.topPages[0].pageUrl", is("https://example.com/post")))
            .andExpect(jsonPath("$.activeCommenters[0].email", is("author@example.com")));

        verify(ownerAnalyticsService).getOwnerAnalytics(currentUser, "30d", siteId, "UTC");
    }

    @Test
    void ownerAnalyticsUsesDefaultRange() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(ownerAnalyticsService.getOwnerAnalytics(eq(currentUser), eq("30d"), isNull(), eq("UTC")))
            .thenReturn(analytics(null));

        mockMvc.perform(get("/api/analytics/owner")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.range", is("30d")))
            .andExpect(jsonPath("$.timeZone", is("UTC")));

        verify(ownerAnalyticsService).getOwnerAnalytics(currentUser, "30d", null, "UTC");
    }

    @Test
    void ownerAnalyticsPassesRequestedTimeZone() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(ownerAnalyticsService.getOwnerAnalytics(currentUser, "90d", null, "Europe/Moscow"))
            .thenReturn(analytics(null));

        mockMvc.perform(get("/api/analytics/owner")
                .param("range", "90d")
                .param("timeZone", "Europe/Moscow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk());

        verify(ownerAnalyticsService).getOwnerAnalytics(currentUser, "90d", null, "Europe/Moscow");
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
    void ownerAnalyticsRejectsOversizedTimeZoneBeforeServiceCall() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(get("/api/analytics/owner")
                .param("timeZone", "x".repeat(65))
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.fields[0].field", is("timeZone")));

        verifyNoInteractions(ownerAnalyticsService);
    }

    @Test
    void ownerAnalyticsMasksForeignSiteAsNotFound() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(ownerAnalyticsService.getOwnerAnalytics(currentUser, "7d", siteId, "UTC"))
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
            "UTC",
            AnalyticsBucket.DAY,
            Instant.parse("2026-06-06T00:00:00Z"),
            Instant.parse("2026-07-05T15:30:00Z"),
            new AnalyticsSummary(1, 2, 12, 3, 7, 2, 8, 1, 0, 1),
            List.of(new CommentTimePoint(LocalDate.parse("2026-07-01"), 5, 4, 1, 0)),
            AnalyticsComparison.between(
                Instant.parse("2026-05-07T00:00:00Z"),
                Instant.parse("2026-06-05T15:30:00Z"),
                new PeriodActivity(12, 7, 6, 4, 1),
                new PeriodActivity(10, 5, 3, 5, 0)
            ),
            new AnalyticsWorkload(3, Instant.parse("2026-07-01T10:00:00Z"), 6, 4, 1),
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
