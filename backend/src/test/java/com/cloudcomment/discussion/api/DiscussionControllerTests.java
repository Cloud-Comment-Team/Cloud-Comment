package com.cloudcomment.discussion.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.discussion.application.DiscussionFilters;
import com.cloudcomment.discussion.application.DiscussionPage;
import com.cloudcomment.discussion.application.DiscussionService;
import com.cloudcomment.discussion.domain.DiscussionAuthor;
import com.cloudcomment.discussion.domain.DiscussionFilter;
import com.cloudcomment.discussion.domain.DiscussionMessage;
import com.cloudcomment.discussion.domain.DiscussionStatus;
import com.cloudcomment.discussion.domain.DiscussionSummary;
import com.cloudcomment.discussion.domain.DiscussionThread;
import com.cloudcomment.discussion.domain.OwnerReplyResult;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.cloudcomment.support.AdminSecurityTestSupport.adminRequest;
import static com.cloudcomment.support.AdminSecurityTestSupport.csrf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class DiscussionControllerTests {

    private static final Instant CREATED_AT = Instant.parse("2026-07-13T12:00:00Z");
    private static final UUID SITE_ID = UUID.fromString("00000000-0000-4000-8000-000000000174");
    private static final UUID PAGE_ID = UUID.fromString("00000000-0000-4000-8000-000000000175");
    private static final UUID ROOT_ID = UUID.fromString("00000000-0000-4000-8000-000000000176");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private DiscussionService discussionService;

    @Test
    void endpointsRequireAdminAuthentication() throws Exception {
        mockMvc.perform(get("/api/discussions"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/discussions/{rootCommentId}", ROOT_ID))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/discussions/{rootCommentId}/replies", ROOT_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"operationId":"00000000-0000-4000-8000-000000000177","content":"Ответ"}
                    """))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(discussionService);
    }

    @Test
    void listsDiscussionsWithStableFiltersAndPublicAuthorIdentity() throws Exception {
        AuthenticatedUser user = currentUser();
        DiscussionSummary summary = summary();
        when(currentUserService.getCurrentUser(eq("session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)))
            .thenReturn(user);
        when(discussionService.list(eq(user), any(DiscussionFilters.class), eq(2), eq(10)))
            .thenReturn(new DiscussionPage(List.of(summary), 2, 10, 11));

        mockMvc.perform(get("/api/discussions")
                .queryParam("siteId", SITE_ID.toString())
                .queryParam("view", "NEEDS_REPLY")
                .queryParam("search", "  статья  ")
                .queryParam("page", "2")
                .queryParam("pageSize", "10")
                .with(adminRequest("session-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].rootCommentId", is(ROOT_ID.toString())))
            .andExpect(jsonPath("$.items[0].siteName", is("Редакция")))
            .andExpect(jsonPath("$.items[0].lastAuthor.displayName", is("Анна")))
            .andExpect(jsonPath("$.items[0].lastAuthor.owner", is(false)))
            .andExpect(jsonPath("$.items[0].status", is("NEEDS_REPLY")))
            .andExpect(jsonPath("$.items[0].unread", is(true)))
            .andExpect(jsonPath("$.totalItems", is(11)))
            .andExpect(content().string(not(containsString("email"))));

        verify(discussionService).list(
            user,
            new DiscussionFilters(SITE_ID, DiscussionFilter.NEEDS_REPLY, "  статья  "),
            2,
            10
        );
    }

    @Test
    void returnsThreadAndMasksForeignRootAsNotFound() throws Exception {
        AuthenticatedUser user = currentUser();
        DiscussionSummary summary = summary();
        DiscussionMessage root = new DiscussionMessage(
            ROOT_ID,
            null,
            summary.lastAuthor(),
            "Текст без публичного адреса",
            CREATED_AT,
            CREATED_AT,
            true
        );
        when(currentUserService.getCurrentUser(eq("session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)))
            .thenReturn(user);
        when(discussionService.get(user, ROOT_ID)).thenReturn(new DiscussionThread(summary, List.of(root)));

        mockMvc.perform(get("/api/discussions/{rootCommentId}", ROOT_ID)
                .with(adminRequest("session-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].content", is("Текст без публичного адреса")))
            .andExpect(jsonPath("$.messages[0].author.displayName", is("Анна")))
            .andExpect(content().string(not(containsString("anna@example.com"))));

        UUID foreignId = UUID.randomUUID();
        when(discussionService.get(user, foreignId))
            .thenThrow(new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));

        mockMvc.perform(get("/api/discussions/{rootCommentId}", foreignId)
                .with(adminRequest("session-token")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void validatesPaginationAndSearchLength() throws Exception {
        when(currentUserService.getCurrentUser(eq("session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)))
            .thenReturn(currentUser());

        mockMvc.perform(get("/api/discussions")
                .queryParam("page", "0")
                .with(adminRequest("session-token")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/discussions")
                .queryParam("search", "x".repeat(121))
                .with(adminRequest("session-token")))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(discussionService);
    }

    @Test
    void createsOwnerReplyAndReturnsIdempotentReplayWithoutPrivateFields() throws Exception {
        AuthenticatedUser user = currentUser();
        UUID operationId = UUID.fromString("00000000-0000-4000-8000-000000000177");
        DiscussionMessage message = new DiscussionMessage(
            UUID.fromString("00000000-0000-4000-8000-000000000178"),
            ROOT_ID,
            new DiscussionAuthor(user.id(), "Автор сайта", true),
            "Спасибо за вопрос",
            CREATED_AT,
            CREATED_AT,
            false
        );
        when(currentUserService.getCurrentUser(eq("session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)))
            .thenReturn(user);
        when(discussionService.reply(user, ROOT_ID, operationId, "Спасибо за вопрос"))
            .thenReturn(
                new OwnerReplyResult(message, SITE_ID, PAGE_ID, true),
                new OwnerReplyResult(message, SITE_ID, PAGE_ID, false)
            );

        String request = """
            {"operationId":"%s","content":"Спасибо за вопрос"}
            """.formatted(operationId);
        mockMvc.perform(post("/api/discussions/{rootCommentId}/replies", ROOT_ID)
                .with(adminRequest("session-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.created", is(true)))
            .andExpect(jsonPath("$.message.author.owner", is(true)))
            .andExpect(jsonPath("$.message.content", is("Спасибо за вопрос")))
            .andExpect(content().string(not(containsString("owner@example.com"))));

        mockMvc.perform(post("/api/discussions/{rootCommentId}/replies", ROOT_ID)
                .with(adminRequest("session-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created", is(false)))
            .andExpect(jsonPath("$.message.id", is(message.id().toString())));
    }

    @Test
    void validatesOwnerReplyRequestBeforeServiceCall() throws Exception {
        when(currentUserService.getCurrentUser(eq("session-token"), eq(com.cloudcomment.auth.domain.SessionAudience.ADMIN)))
            .thenReturn(currentUser());

        mockMvc.perform(post("/api/discussions/{rootCommentId}/replies", ROOT_ID)
                .with(adminRequest("session-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));

        verifyNoInteractions(discussionService);
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), CREATED_AT, CREATED_AT);
    }

    private DiscussionSummary summary() {
        return new DiscussionSummary(
            ROOT_ID,
            SITE_ID,
            "Редакция",
            PAGE_ID,
            "https://example.com/article",
            "Новая статья",
            new DiscussionAuthor(UUID.randomUUID(), "Анна", false),
            "Интересная публикация",
            CREATED_AT,
            2,
            true,
            DiscussionStatus.NEEDS_REPLY,
            true
        );
    }
}
