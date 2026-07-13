package com.cloudcomment.comment.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.CommentPermalinkLocation;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.comment.application.PublicCommentService;
import com.cloudcomment.comment.application.PublicWidgetConfig;
import com.cloudcomment.comment.domain.CommentAuthor;
import com.cloudcomment.comment.domain.CommentReactionSummary;
import com.cloudcomment.comment.domain.CommentReactionType;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.comment.domain.PublicCommentSort;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.widgetcontext.application.ResolvedWidgetContext;
import com.cloudcomment.widgetcontext.application.WidgetContextService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminSession;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "cloud-comment.embed.api-base-url=https://api.example.net/api",
    "cloud-comment.embed.widget-base-url=https://widget.example.net"
})
@AutoConfigureMockMvc
class PublicCommentControllerTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");
    private static final String ORIGIN = "https://example.com";
    private static final String FRAME_ORIGIN = "https://widget.example.net";
    private static final String PAGE_URL = "https://example.com/blog/post-1";
    private static final String CONTEXT_TOKEN = "frame-context-token";
    private static final UUID CONTEXT_ID = UUID.fromString("481d7d35-faf6-443b-bace-6c541e109513");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private PublicCommentService publicCommentService;

    @MockitoBean
    private DomainPolicyService domainPolicyService;

    @MockitoBean
    private WidgetContextService widgetContextService;

    @BeforeEach
    void setUpWidgetContext() {
        when(widgetContextService.acceptsFrameOrigin(FRAME_ORIGIN)).thenReturn(true);
        when(widgetContextService.resolve(any(UUID.class), eq(CONTEXT_TOKEN))).thenAnswer(invocation ->
            new ResolvedWidgetContext(
                CONTEXT_ID,
                invocation.getArgument(0),
                ORIGIN,
                "a".repeat(64),
                TIMESTAMP.plusSeconds(7200)
            )
        );
        when(widgetContextService.matchesPage(any(ResolvedWidgetContext.class), eq(PAGE_URL))).thenReturn(true);
        when(widgetContextService.matchesPageHash("a".repeat(64), PAGE_URL)).thenReturn(true);
    }

    @Test
    void configIsPublicButRequiresAllowedOrigin() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(publicCommentService.getConfig(siteId, ORIGIN))
            .thenReturn(new PublicWidgetConfig(siteId, ModerationMode.PRE_MODERATION));

        mockMvc.perform(get("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(jsonPath("$.siteId", is(siteId.toString())))
            .andExpect(jsonPath("$.moderationMode", is("PRE_MODERATION")));

        verify(domainPolicyService).recordSuccessfulInstallation(siteId, ORIGIN);
        verifyNoInteractions(currentUserService);
    }

    @Test
    void configAllowsSameOriginBrowserRequestWithoutOriginUsingReferer() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(publicCommentService.getConfig(siteId, ORIGIN))
            .thenReturn(new PublicWidgetConfig(siteId, ModerationMode.PRE_MODERATION));

        mockMvc.perform(get("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.REFERER, PAGE_URL))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(jsonPath("$.siteId", is(siteId.toString())))
            .andExpect(jsonPath("$.moderationMode", is("PRE_MODERATION")));

        verify(domainPolicyService).recordSuccessfulInstallation(siteId, ORIGIN);
        verifyNoInteractions(currentUserService);
    }

    @Test
    void configHeadDoesNotCreateSuccessfulInstallationSignal() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(publicCommentService.getConfig(siteId, ORIGIN))
            .thenReturn(new PublicWidgetConfig(siteId, ModerationMode.PRE_MODERATION));

        mockMvc.perform(head("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isOk());

        verify(domainPolicyService, org.mockito.Mockito.never())
            .recordSuccessfulInstallation(siteId, ORIGIN);
    }

    @Test
    void rejectedConfigRequestRecordsOnlyTheNormalizedInstallationSignal() throws Exception {
        UUID siteId = UUID.randomUUID();
        String rejectedOrigin = "https://evil.example";
        when(domainPolicyService.isOriginAllowed(siteId, rejectedOrigin)).thenReturn(false);

        mockMvc.perform(get("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.ORIGIN, rejectedOrigin))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verify(domainPolicyService).recordRejectedInstallation(siteId, rejectedOrigin);
        verifyNoInteractions(currentUserService, publicCommentService);
    }

    @Test
    void listCommentsReturnsPaginatedApprovedComments() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        CommentView comment = comment(siteId, pageId, null, CommentStatus.APPROVED);
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(publicCommentService.listComments(siteId, ORIGIN, PAGE_URL, 1, 20, PublicCommentSort.NEWEST, Optional.empty(), null))
            .thenReturn(new CommentPage(List.of(comment), 1, 20, 1));

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .param("pageUrl", PAGE_URL)
                .param("sort", "NEWEST"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id", is(comment.id().toString())))
            .andExpect(jsonPath("$.items[0].siteId", is(siteId.toString())))
            .andExpect(jsonPath("$.items[0].pageId", is(pageId.toString())))
            .andExpect(jsonPath("$.items[0].author.email").doesNotExist())
            .andExpect(jsonPath("$.items[0].author.displayName", is("Участник")))
            .andExpect(jsonPath("$.items[0].content", is("Hello world")))
            .andExpect(jsonPath("$.items[0].status", is("APPROVED")))
            .andExpect(jsonPath("$.items[0].pinned", is(false)))
            .andExpect(jsonPath("$.items[0].favorite").doesNotExist())
            .andExpect(jsonPath("$.items[0].replyCount", is(0)))
            .andExpect(jsonPath("$.items[0].replies", empty()))
            .andExpect(jsonPath("$.page", is(1)))
            .andExpect(jsonPath("$.pageSize", is(20)))
            .andExpect(jsonPath("$.totalItems", is(1)))
            .andExpect(jsonPath("$.totalPages", is(1)));

        verifyNoInteractions(currentUserService);
    }

    @Test
    void listCommentsRejectsInvalidOptionalBearerInsteadOfDowngradingToAnonymous() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(currentUserService.getWidgetCurrentUser(
            "expired-session-token",
            siteId,
            ORIGIN
        ))
            .thenThrow(new ApplicationException(ApiErrorCode.INVALID_SESSION, "Invalid or expired session"));

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-session-token")
                .param("pageUrl", PAGE_URL))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        verifyNoInteractions(publicCommentService);
    }

    @Test
    void listCommentsPassesWidgetViewerAndReturnsOwnedPendingComment() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        CommentView pending = ownedPendingComment(siteId, pageId, null, currentUser);
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);
        when(publicCommentService.listComments(
            siteId,
            ORIGIN,
            PAGE_URL,
            1,
            20,
            PublicCommentSort.PINNED_FIRST,
            Optional.of(currentUser.id()),
            null
        )).thenReturn(new CommentPage(List.of(pending), 1, 20, 1));

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .param("pageUrl", PAGE_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].status", is("PENDING")))
            .andExpect(jsonPath("$.items[0].ownedByCurrentUser", is(true)))
            .andExpect(jsonPath("$.items[0].author.email").doesNotExist())
            .andExpect(jsonPath("$.totalItems", is(1)));
    }

    @Test
    void listCommentsPassesOptionalReplyLimit() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(publicCommentService.listComments(
            siteId, ORIGIN, PAGE_URL, 1, 20, PublicCommentSort.PINNED_FIRST, Optional.empty(), 3
        )).thenReturn(new CommentPage(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .param("pageUrl", PAGE_URL)
                .param("replyLimit", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void permalinkReturnsRootAndReplyPagesInsideWidgetContext() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        when(publicCommentService.locateComment(
            siteId, ORIGIN, PAGE_URL, replyId, 20, PublicCommentSort.NEWEST, Optional.empty()
        )).thenReturn(new CommentPermalinkLocation(replyId, rootId, 3, 2));

        mockMvc.perform(get("/api/public/sites/{siteId}/comments/{commentId}/permalink", siteId, replyId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .queryParam("pageUrl", PAGE_URL)
                .queryParam("sort", "NEWEST")
                .queryParam("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(jsonPath("$.commentId", is(replyId.toString())))
            .andExpect(jsonPath("$.rootCommentId", is(rootId.toString())))
            .andExpect(jsonPath("$.rootPage", is(3)))
            .andExpect(jsonPath("$.replyPage", is(2)));
    }

    @Test
    void listRepliesReturnsOwnerScopedPage() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        CommentView reply = comment(siteId, pageId, rootId, CommentStatus.APPROVED);
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(publicCommentService.listReplies(siteId, ORIGIN, rootId, 1, 20, Optional.empty()))
            .thenReturn(new CommentPage(List.of(reply), 1, 20, 1));

        mockMvc.perform(get("/api/public/sites/{siteId}/comments/{commentId}/replies", siteId, rootId)
                .header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].parentId", is(rootId.toString())))
            .andExpect(jsonPath("$.totalItems", is(1)));
    }

    @Test
    void listRepliesPassesWidgetViewerAndReturnsOwnedPendingReply() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        CommentView pendingReply = ownedPendingComment(siteId, pageId, rootId, currentUser);
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);
        when(publicCommentService.listReplies(
            siteId, ORIGIN, rootId, 1, 20, Optional.of(currentUser.id())
        )).thenReturn(new CommentPage(List.of(pendingReply), 1, 20, 1));

        mockMvc.perform(get("/api/public/sites/{siteId}/comments/{commentId}/replies", siteId, rootId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].status", is("PENDING")))
            .andExpect(jsonPath("$.items[0].ownedByCurrentUser", is(true)))
            .andExpect(jsonPath("$.items[0].author.email").doesNotExist())
            .andExpect(jsonPath("$.totalItems", is(1)));
    }

    @Test
    void listCommentsRejectsInvalidPageUrlWithValidationEnvelope() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .param("pageUrl", "not-a-url"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/public/sites/" + siteId + "/pages/comments")))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());

        verifyNoInteractions(currentUserService, publicCommentService);
    }

    @Test
    void createCommentRequiresBearerToken() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageUrl": "%s",
                      "content": "Hello world"
                    }
                    """.formatted(PAGE_URL)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.path", is("/api/public/sites/" + siteId + "/pages/comments")))
            .andExpect(jsonPath("$.error.fields", empty()));

        verifyNoInteractions(publicCommentService);
    }

    @Test
    void adminCookieAloneCannotAuthorizePublicWidgetWrite() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .with(adminSession("admin-session-token"))
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageUrl": "%s",
                      "content": "Must stay unauthorized"
                    }
                    """.formatted(PAGE_URL)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        verifyNoInteractions(publicCommentService, currentUserService);
    }

    @Test
    void createCommentReturnsCreatedCommentForAuthenticatedUser() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        CommentView created = comment(siteId, pageId, null, CommentStatus.PENDING);
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);
        when(publicCommentService.createComment(currentUser, siteId, ORIGIN, PAGE_URL, null, "Hello world"))
            .thenReturn(created);

        mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageUrl": "%s",
                      "content": "Hello world"
                    }
                    """.formatted(PAGE_URL)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(created.id().toString())))
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.content", is("Hello world")));
    }

    @Test
    void createCommentPassesParentIdToService() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        CommentView created = comment(siteId, pageId, parentId, CommentStatus.PENDING);
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);
        when(publicCommentService.createComment(currentUser, siteId, ORIGIN, PAGE_URL, parentId, "Reply body"))
            .thenReturn(created);

        mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageUrl": "%s",
                      "parentId": "%s",
                      "content": "Reply body"
                    }
                    """.formatted(PAGE_URL, parentId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(created.id().toString())))
            .andExpect(jsonPath("$.parentId", is(parentId.toString())))
            .andExpect(jsonPath("$.content", is("Hello world")));
    }

    @Test
    void setReactionRequiresBearerToken() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(put("/api/public/sites/{siteId}/comments/{commentId}/reaction", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "LOVE"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        verifyNoInteractions(publicCommentService);
    }

    @Test
    void setReactionReturnsUpdatedReactionSummary() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);
        when(publicCommentService.setReaction(currentUser, siteId, ORIGIN, commentId, CommentReactionType.LOVE))
            .thenReturn(List.of(
                new CommentReactionSummary(CommentReactionType.LIKE, 2, false),
                new CommentReactionSummary(CommentReactionType.LOVE, 1, true)
            ));

        mockMvc.perform(put("/api/public/sites/{siteId}/comments/{commentId}/reaction", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "LOVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reactions[0].type", is("LIKE")))
            .andExpect(jsonPath("$.reactions[0].count", is(2)))
            .andExpect(jsonPath("$.reactions[0].reactedByCurrentUser", is(false)))
            .andExpect(jsonPath("$.reactions[1].type", is("LOVE")))
            .andExpect(jsonPath("$.reactions[1].count", is(1)))
            .andExpect(jsonPath("$.reactions[1].reactedByCurrentUser", is(true)));
    }

    @Test
    void setReactionWithNullTypeClearsExistingReaction() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);
        when(publicCommentService.setReaction(currentUser, siteId, ORIGIN, commentId, null))
            .thenReturn(List.of());

        mockMvc.perform(put("/api/public/sites/{siteId}/comments/{commentId}/reaction", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reactions", empty()));
    }

    @Test
    void updateOwnCommentRequiresBearerToken() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(patch("/api/public/sites/{siteId}/comments/{commentId}", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "Updated body"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        verifyNoInteractions(publicCommentService);
    }

    @Test
    void updateOwnCommentReturnsUpdatedCommentForAuthenticatedUser() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        CommentView updated = comment(siteId, pageId, null, CommentStatus.PENDING, commentId, "Updated body");
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);
        when(publicCommentService.updateOwnComment(currentUser, siteId, ORIGIN, commentId, "Updated body"))
            .thenReturn(updated);

        mockMvc.perform(patch("/api/public/sites/{siteId}/comments/{commentId}", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "Updated body"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                "GET, POST, PUT, PATCH, DELETE, OPTIONS"
            ))
            .andExpect(jsonPath("$.id", is(commentId.toString())))
            .andExpect(jsonPath("$.content", is("Updated body")))
            .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void updateOwnCommentRejectsInvalidContentWithValidationEnvelope() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);

        mockMvc.perform(patch("/api/public/sites/{siteId}/comments/{commentId}", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/public/sites/" + siteId + "/comments/" + commentId)))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());

        verifyNoInteractions(publicCommentService);
    }

    @Test
    void deleteOwnCommentRequiresBearerToken() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(delete("/api/public/sites/{siteId}/comments/{commentId}", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));

        verifyNoInteractions(publicCommentService);
    }

    @Test
    void deleteOwnCommentReturnsNoContentForAuthenticatedUser() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);

        mockMvc.perform(delete("/api/public/sites/{siteId}/comments/{commentId}", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                "GET, POST, PUT, PATCH, DELETE, OPTIONS"
            ));

        verify(publicCommentService).deleteOwnComment(currentUser, siteId, ORIGIN, commentId);
    }

    @Test
    void contextRequestWithoutPageHeaderIsRejectedBeforeController() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        mockMvc.perform(get("/api/public/sites/{siteId}/comments/{commentId}/replies", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));

        verifyNoInteractions(publicCommentService);
    }

    @Test
    void dedicatedUnsafeRequestStillRequiresExplicitOriginEvenOnWidgetHost() throws Exception {
        UUID siteId = UUID.randomUUID();

        mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.HOST, "widget.example.net")
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"pageUrl":"%s","content":"Must not be created"}
                    """.formatted(PAGE_URL)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));

        verify(widgetContextService).acceptsContextTransport(null, "widget.example.net", false);
        verifyNoInteractions(currentUserService, publicCommentService);
    }

    @Test
    void dedicatedSafeRequestWithoutOriginUsesWidgetHostAndDoesNotEmitCorsHeader() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(widgetContextService.acceptsContextTransport(null, "widget.example.net", true)).thenReturn(true);
        when(publicCommentService.getConfig(siteId, ORIGIN))
            .thenReturn(new PublicWidgetConfig(siteId, ModerationMode.PRE_MODERATION));

        mockMvc.perform(get("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.HOST, "widget.example.net")
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.siteId", is(siteId.toString())));

        verify(widgetContextService).acceptsContextTransport(null, "widget.example.net", true);
        verify(publicCommentService).getConfig(siteId, ORIGIN);
        verifyNoInteractions(currentUserService);
    }

    @Test
    void contextRequestWithDifferentPageHeaderIsRejectedBeforeAuthentication() throws Exception {
        UUID siteId = UUID.randomUUID();
        String otherPage = ORIGIN + "/blog/post-2";

        mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", otherPage)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"pageUrl":"%s","content":"Must not be created"}
                    """.formatted(PAGE_URL)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));

        verifyNoInteractions(currentUserService, publicCommentService);
    }

    @Test
    void bodyPageDifferentFromValidatedContextIsRejectedAfterAuthentication() throws Exception {
        UUID siteId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        String otherPage = ORIGIN + "/blog/post-2";
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser);

        mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"pageUrl":"%s","content":"Must not be created"}
                    """.formatted(otherPage)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));

        verifyNoInteractions(publicCommentService);
    }

    @Test
    void repliesRejectKnownCommentFromAnotherContextPage() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        rejectCommentFromContextPage(siteId, commentId);

        mockMvc.perform(get("/api/public/sites/{siteId}/comments/{commentId}/replies", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));
    }

    @Test
    void reactionRejectsKnownCommentFromAnotherContextPage() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        mockAuthenticatedWidgetUser(siteId);
        rejectCommentFromContextPage(siteId, commentId);

        mockMvc.perform(put("/api/public/sites/{siteId}/comments/{commentId}/reaction", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LIKE\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));
    }

    @Test
    void updateRejectsKnownCommentFromAnotherContextPage() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        mockAuthenticatedWidgetUser(siteId);
        rejectCommentFromContextPage(siteId, commentId);

        mockMvc.perform(patch("/api/public/sites/{siteId}/comments/{commentId}", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Updated body\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));
    }

    @Test
    void deleteRejectsKnownCommentFromAnotherContextPage() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        mockAuthenticatedWidgetUser(siteId);
        rejectCommentFromContextPage(siteId, commentId);

        mockMvc.perform(delete("/api/public/sites/{siteId}/comments/{commentId}", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));
    }

    @Test
    void invalidSiteOrOriginScopedBearerIsRejectedWithoutAnonymousDowngrade() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(currentUserService.getWidgetCurrentUser(
            "other-context-token", siteId, ORIGIN
        )).thenThrow(new ApplicationException(ApiErrorCode.INVALID_SESSION, "Invalid or expired session"));

        mockMvc.perform(put("/api/public/sites/{siteId}/comments/{commentId}/reaction", siteId, commentId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-CloudComment-Page-Url", PAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer other-context-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LIKE\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));
    }

    @Test
    void serviceNotFoundIsReturnedAsUnifiedNotFound() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(publicCommentService.getConfig(siteId, ORIGIN))
            .thenThrow(new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));

        mockMvc.perform(get("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")))
            .andExpect(jsonPath("$.error.fields", empty()));

        verify(domainPolicyService, org.mockito.Mockito.never())
            .recordSuccessfulInstallation(siteId, ORIGIN);
    }

    @Test
    void preflightForAllowedOriginReturnsCorsHeadersWithoutBearer() throws Exception {
        UUID siteId = UUID.randomUUID();
        mockMvc.perform(options("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    "Authorization, Content-Type, X-CloudComment-Widget-Context, X-CloudComment-Page-Url"
                ))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                "GET, POST, PUT, PATCH, DELETE, OPTIONS"
            ))
            .andExpect(header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                "Authorization, Content-Type, Accept, X-CloudComment-Widget-Context, X-CloudComment-Page-Url"
            ));

        verifyNoInteractions(currentUserService, publicCommentService);
        verify(domainPolicyService, org.mockito.Mockito.never())
            .recordSuccessfulInstallation(siteId, ORIGIN);
    }

    @Test
    void framePreflightSupportsPatchAndDelete() throws Exception {
        UUID siteId = UUID.randomUUID();

        for (String method : java.util.List.of("PATCH", "DELETE")) {
            mockMvc.perform(options("/api/public/sites/{siteId}/comments/{commentId}", siteId, UUID.randomUUID())
                    .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                    .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "authorization, content-type, accept, X-CloudComment-Widget-Context, X-CloudComment-Page-Url"
                    ))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
                .andExpect(header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                    "GET, POST, PUT, PATCH, DELETE, OPTIONS"
                ))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        verifyNoInteractions(currentUserService, publicCommentService);
    }

    @Test
    void preflightRejectsUnknownMethodAndHeaderWithoutCorsHeaders() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(options("/api/public/sites/{siteId}/comments/{commentId}", siteId, UUID.randomUUID())
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "TRACE"))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mockMvc.perform(options("/api/public/sites/{siteId}/comments/{commentId}", siteId, UUID.randomUUID())
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, X-Admin-Token"))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verifyNoInteractions(currentUserService, publicCommentService);
    }

    @Test
    void preflightForDisallowedOriginDoesNotReturnPermissiveCorsHeaders() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, "https://evil.example")).thenReturn(false);

        mockMvc.perform(options("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, WidgetContextService.CONTEXT_HEADER))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verifyNoInteractions(currentUserService, publicCommentService);
        verify(domainPolicyService, org.mockito.Mockito.never())
            .recordRejectedInstallation(siteId, "https://evil.example");
    }

    @Test
    void rejectedConfigPreflightRecordsInstallationSignal() throws Exception {
        UUID siteId = UUID.randomUUID();
        String rejectedOrigin = "https://evil.example";
        when(domainPolicyService.isOriginAllowed(siteId, rejectedOrigin)).thenReturn(false);

        mockMvc.perform(options("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.ORIGIN, rejectedOrigin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verify(domainPolicyService).recordRejectedInstallation(siteId, rejectedOrigin);
        verifyNoInteractions(currentUserService, publicCommentService);
    }

    @Test
    void unsupportedConfigMethodsDoNotCreateInstallationSignals() throws Exception {
        UUID siteId = UUID.randomUUID();
        String rejectedOrigin = "https://evil.example";
        when(domainPolicyService.isOriginAllowed(siteId, rejectedOrigin)).thenReturn(false);

        mockMvc.perform(post("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.ORIGIN, rejectedOrigin))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));
        mockMvc.perform(options("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.ORIGIN, rejectedOrigin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verify(domainPolicyService, org.mockito.Mockito.never())
            .recordRejectedInstallation(siteId, rejectedOrigin);
        verifyNoInteractions(currentUserService, publicCommentService);
    }

    @Test
    void actualPublicRequestWithDisallowedOriginIsMaskedBeforeControllerValidation() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, "https://evil.example")).thenReturn(false);

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .param("pageUrl", "not-a-url"))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")))
            .andExpect(jsonPath("$.error.fields", empty()));

        verifyNoInteractions(currentUserService, publicCommentService);
        verify(domainPolicyService, org.mockito.Mockito.never())
            .recordRejectedInstallation(siteId, "https://evil.example");
    }

    @Test
    void actualPublicRequestWithoutOriginIsMaskedBeforeAuthentication() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, null)).thenReturn(false);

        mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageUrl": "%s",
                      "content": "Hello world"
                    }
                    """.formatted(PAGE_URL)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")))
            .andExpect(jsonPath("$.error.message", is("Invalid widget context")));

        verifyNoInteractions(currentUserService, publicCommentService);
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "visitor@example.com", Set.of("COMMENTER"), TIMESTAMP, TIMESTAMP);
    }

    private void mockAuthenticatedWidgetUser(UUID siteId) {
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token", siteId, ORIGIN
        )).thenReturn(currentUser());
    }

    private void rejectCommentFromContextPage(UUID siteId, UUID commentId) {
        doThrow(new ApplicationException(ApiErrorCode.INVALID_WIDGET_CONTEXT, "Invalid widget context"))
            .when(publicCommentService).assertCommentBelongsToContextPage(siteId, commentId, PAGE_URL);
    }

    private CommentView comment(UUID siteId, UUID pageId, UUID parentId, CommentStatus status) {
        return comment(siteId, pageId, parentId, status, UUID.randomUUID(), "Hello world");
    }

    private CommentView comment(
        UUID siteId,
        UUID pageId,
        UUID parentId,
        CommentStatus status,
        UUID commentId,
        String content
    ) {
        return new CommentView(
            commentId,
            siteId,
            pageId,
            parentId,
            new CommentAuthor(UUID.randomUUID(), "visitor@example.com", "visitor@example.com"),
            content,
            status,
            TIMESTAMP,
            TIMESTAMP,
            List.of()
        );
    }

    private CommentView ownedPendingComment(
        UUID siteId,
        UUID pageId,
        UUID parentId,
        AuthenticatedUser currentUser
    ) {
        return new CommentView(
            UUID.randomUUID(),
            siteId,
            pageId,
            parentId,
            new CommentAuthor(currentUser.id(), currentUser.email(), currentUser.email()),
            "Waiting for moderation",
            CommentStatus.PENDING,
            TIMESTAMP,
            TIMESTAMP,
            null,
            false,
            true,
            List.of(),
            0,
            List.of()
        );
    }
}
