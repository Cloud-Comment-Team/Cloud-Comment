package com.cloudcomment.notification.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.notification.application.OwnerNotificationPage;
import com.cloudcomment.notification.application.OwnerNotificationService;
import com.cloudcomment.notification.domain.OwnerNotificationView;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminRequest;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class NotificationControllerTests {

    private static final Instant CREATED_AT = Instant.parse("2026-07-12T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private OwnerNotificationService service;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void listsOwnerNotificationsAndUnreadCount() throws Exception {
        AuthenticatedUser user = currentUser();
        OwnerNotificationView notification = notification();
        when(currentUserService.getCurrentUser(eq("session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN))).thenReturn(user);
        when(service.list(user, 1, 20)).thenReturn(new OwnerNotificationPage(List.of(notification), 1, 20, 1));
        when(service.unreadCount(user)).thenReturn(1L);

        mockMvc.perform(get("/api/notifications")
                .with(adminRequest("session-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id", is(notification.id().toString())))
            .andExpect(jsonPath("$.items[0].commentId", is(notification.commentId().toString())))
            .andExpect(jsonPath("$.items[0].siteName", is("Сайт")))
            .andExpect(jsonPath("$.items[0].contentPreview", is("Текст комментария")))
            .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
            .andExpect(jsonPath("$.totalItems", is(1)));

        mockMvc.perform(get("/api/notifications/unread-count")
                .with(adminRequest("session-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount", is(1)));
    }

    @Test
    void marksOneOrAllNotificationsRead() throws Exception {
        AuthenticatedUser user = currentUser();
        OwnerNotificationView notification = notification();
        OwnerNotificationView read = new OwnerNotificationView(
            notification.id(), notification.commentId(), notification.siteId(), notification.siteName(),
            notification.pageId(), notification.pageUrl(), notification.parentId(), notification.authorEmail(),
            notification.content(), notification.status(), CREATED_AT.plusSeconds(60), notification.createdAt()
        );
        when(currentUserService.getCurrentUser(eq("session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN))).thenReturn(user);
        when(service.markRead(user, notification.id())).thenReturn(read);

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notification.id())
                .with(adminRequest("session-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readAt", is("2026-07-12T12:01:00Z")));

        mockMvc.perform(post("/api/notifications/read-all")
                .with(adminRequest("session-token")))
            .andExpect(status().isNoContent());
        verify(service).markAllRead(user);
    }

    @Test
    void masksForeignNotificationAsNotFound() throws Exception {
        AuthenticatedUser user = currentUser();
        UUID notificationId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN))).thenReturn(user);
        when(service.markRead(user, notificationId))
            .thenThrow(new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                .with(adminRequest("session-token")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), CREATED_AT, CREATED_AT);
    }

    private OwnerNotificationView notification() {
        return new OwnerNotificationView(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Сайт", UUID.randomUUID(),
            "https://example.com/page", null, "author@example.com", "Текст комментария",
            CommentStatus.PENDING, null, CREATED_AT
        );
    }
}
