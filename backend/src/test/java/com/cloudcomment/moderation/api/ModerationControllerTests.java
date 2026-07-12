package com.cloudcomment.moderation.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.moderation.application.ModerationCommentFilters;
import com.cloudcomment.moderation.application.ModerationCommentPage;
import com.cloudcomment.moderation.application.ModerationService;
import com.cloudcomment.moderation.domain.Comment;
import com.cloudcomment.moderation.domain.CommentAuthor;
import com.cloudcomment.moderation.domain.CommentSortField;
import com.cloudcomment.moderation.domain.CommentStatus;
import com.cloudcomment.moderation.domain.ModerationAction;
import com.cloudcomment.moderation.domain.ModerationActionType;
import com.cloudcomment.moderation.domain.ModerationPriority;
import com.cloudcomment.moderation.domain.ParentComment;
import com.cloudcomment.moderation.domain.SortOrder;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
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

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class ModerationControllerTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ModerationService moderationService;

    @Test
    void moderationEndpointsRequireBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/moderation/comments"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.path", is("/api/moderation/comments")));

        verifyNoInteractions(moderationService);
    }

    @Test
    void listCommentsReturnsPaginatedCommentsForCurrentUser() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        Comment comment = comment(currentUser.id());
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(moderationService.listComments(
            eq(currentUser),
            eq(new ModerationCommentFilters(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                CommentSortField.SMART,
                SortOrder.DESC
            )),
            eq(1),
            eq(20)
        )).thenReturn(new ModerationCommentPage(List.of(comment), 1, 20, 1));

        mockMvc.perform(get("/api/moderation/comments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id", is(comment.id().toString())))
            .andExpect(jsonPath("$.items[0].siteId", is(comment.siteId().toString())))
            .andExpect(jsonPath("$.items[0].pageId", is(comment.pageId().toString())))
            .andExpect(jsonPath("$.items[0].pageUrl", is("https://example.com/page")))
            .andExpect(jsonPath("$.items[0].author.email", is("author@example.com")))
            .andExpect(jsonPath("$.items[0].content", is("Comment body")))
            .andExpect(jsonPath("$.items[0].status", is("PENDING")))
            .andExpect(jsonPath("$.items[0].moderationReason", is(comment.moderationReason())))
            .andExpect(jsonPath("$.items[0].priority", is("HIGH")))
            .andExpect(jsonPath("$.items[0].priorityScore", is(680)))
            .andExpect(jsonPath("$.items[0].priorityReasons[0]", is("Ожидает решения модератора")))
            .andExpect(jsonPath("$.items[0].replies", empty()))
            .andExpect(jsonPath("$.page", is(1)))
            .andExpect(jsonPath("$.pageSize", is(20)))
            .andExpect(jsonPath("$.totalItems", is(1)))
            .andExpect(jsonPath("$.totalPages", is(1)));
    }

    @Test
    void listCommentsReturnsParentSummaryForReply() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        Comment comment = replyComment(currentUser.id());
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(moderationService.listComments(
            eq(currentUser),
            eq(new ModerationCommentFilters(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                CommentSortField.SMART,
                SortOrder.DESC
            )),
            eq(1),
            eq(20)
        )).thenReturn(new ModerationCommentPage(List.of(comment), 1, 20, 1));

        mockMvc.perform(get("/api/moderation/comments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].parentId", is(comment.parentId().toString())))
            .andExpect(jsonPath("$.items[0].parent.id", is(comment.parent().id().toString())))
            .andExpect(jsonPath("$.items[0].parent.author.email", is("parent@example.com")))
            .andExpect(jsonPath("$.items[0].parent.content", is("Parent comment")))
            .andExpect(jsonPath("$.items[0].parent.status", is("APPROVED")));
    }

    @Test
    void listCommentsPassesFiltersToService() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(moderationService.listComments(
            eq(currentUser),
            eq(new ModerationCommentFilters(
                siteId,
                null,
                "https://example.com/page",
                CommentStatus.PENDING,
                Instant.parse("2026-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T23:59:59Z"),
                "widget",
                CommentSortField.SMART,
                SortOrder.DESC
            )),
            eq(2),
            eq(10)
        )).thenReturn(new ModerationCommentPage(List.of(), 2, 10, 0));

        mockMvc.perform(get("/api/moderation/comments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .param("siteId", siteId.toString())
                .param("pageUrl", "https://example.com/page")
                .param("status", "PENDING")
                .param("createdFrom", "2026-06-28T00:00:00Z")
                .param("createdTo", "2026-06-28T23:59:59Z")
                .param("search", "widget")
                .param("page", "2")
                .param("pageSize", "10"))
            .andExpect(status().isOk());

        verify(moderationService).listComments(
            eq(currentUser),
            eq(new ModerationCommentFilters(
                siteId,
                null,
                "https://example.com/page",
                CommentStatus.PENDING,
                Instant.parse("2026-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T23:59:59Z"),
                "widget",
                CommentSortField.SMART,
                SortOrder.DESC
            )),
            eq(2),
            eq(10)
        );
    }

    @Test
    void listCommentsRejectsInvalidPagination() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(get("/api/moderation/comments")
                .param("pageSize", "101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/moderation/comments")));

        verifyNoInteractions(moderationService);
    }

    @Test
    void getCommentReturnsNotFoundForForeignOrMissingComment() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID commentId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(moderationService.getComment(currentUser, commentId))
            .thenThrow(new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));

        mockMvc.perform(get("/api/moderation/comments/{commentId}", commentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")))
            .andExpect(jsonPath("$.error.path", is("/api/moderation/comments/" + commentId)))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void getCommentReturnsCommentDetails() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        Comment comment = comment(currentUser.id());
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(moderationService.getComment(currentUser, comment.id())).thenReturn(comment);

        mockMvc.perform(get("/api/moderation/comments/{commentId}", comment.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(comment.id().toString())))
            .andExpect(jsonPath("$.pageUrl", is("https://example.com/page")))
            .andExpect(jsonPath("$.content", is("Comment body")))
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.moderationReason", is(comment.moderationReason())))
            .andExpect(jsonPath("$.priority", is("HIGH")))
            .andExpect(jsonPath("$.priorityReasons[0]", is("Ожидает решения модератора")));
    }

    @Test
    void getCommentReturnsParentSummaryForReply() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        Comment comment = replyComment(currentUser.id());
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(moderationService.getComment(currentUser, comment.id())).thenReturn(comment);

        mockMvc.perform(get("/api/moderation/comments/{commentId}", comment.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parentId", is(comment.parentId().toString())))
            .andExpect(jsonPath("$.parent.id", is(comment.parent().id().toString())))
            .andExpect(jsonPath("$.parent.author.email", is("parent@example.com")))
            .andExpect(jsonPath("$.parent.content", is("Parent comment")))
            .andExpect(jsonPath("$.parent.status", is("APPROVED")));
    }

    @Test
    void applyActionReturnsCreatedModerationAction() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID commentId = UUID.randomUUID();
        ModerationAction action = new ModerationAction(
            UUID.randomUUID(),
            commentId,
            ModerationActionType.APPROVE,
            CommentStatus.PENDING,
            CommentStatus.APPROVED,
            "Looks good",
            currentUser.id(),
            currentUser.email(),
            TIMESTAMP
        );
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(moderationService.applyAction(currentUser, commentId, ModerationActionType.APPROVE, "Looks good"))
            .thenReturn(action);

        mockMvc.perform(post("/api/moderation/comments/{commentId}/actions", commentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "action": "APPROVE",
                      "reason": "Looks good"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(action.id().toString())))
            .andExpect(jsonPath("$.commentId", is(commentId.toString())))
            .andExpect(jsonPath("$.action", is("APPROVE")))
            .andExpect(jsonPath("$.fromStatus", is("PENDING")))
            .andExpect(jsonPath("$.toStatus", is("APPROVED")))
            .andExpect(jsonPath("$.reason", is("Looks good")))
            .andExpect(jsonPath("$.performedBy.id", is(currentUser.id().toString())))
            .andExpect(jsonPath("$.performedBy.email", is("owner@example.com")));
    }

    @Test
    void applyActionRejectsInvalidRequest() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID commentId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(post("/api/moderation/comments/{commentId}/actions", commentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/moderation/comments/" + commentId + "/actions")));

        verifyNoInteractions(moderationService);
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), TIMESTAMP, TIMESTAMP);
    }

    private Comment comment(UUID ownerId) {
        return new Comment(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "https://example.com/page",
            null,
            null,
            new CommentAuthor(ownerId, "author@example.com", "Author"),
            "Comment body",
            CommentStatus.PENDING,
            "Автомодерация: Спам-маркер: казино / азартные игры",
            ModerationPriority.HIGH,
            680,
            List.of("Ожидает решения модератора", "Есть объяснение автомодерации"),
            TIMESTAMP,
            TIMESTAMP
        );
    }

    private Comment replyComment(UUID ownerId) {
        UUID parentId = UUID.randomUUID();
        return new Comment(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "https://example.com/page",
            parentId,
            new ParentComment(
                parentId,
                new CommentAuthor(UUID.randomUUID(), "parent@example.com", "Parent"),
                "Parent comment",
                CommentStatus.APPROVED,
                TIMESTAMP
            ),
            new CommentAuthor(ownerId, "reply@example.com", "Reply Author"),
            "Reply body",
            CommentStatus.PENDING,
            "Автомодерация: Токсичный маркер: оскорбление",
            ModerationPriority.HIGH,
            715,
            List.of("Ожидает решения модератора", "Есть объяснение автомодерации", "Ответ внутри обсуждения"),
            TIMESTAMP,
            TIMESTAMP
        );
    }
}
