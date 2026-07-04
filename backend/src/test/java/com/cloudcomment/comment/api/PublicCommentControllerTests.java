package com.cloudcomment.comment.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.comment.application.CommentPage;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.comment.application.PublicCommentService;
import com.cloudcomment.comment.application.PublicWidgetConfig;
import com.cloudcomment.comment.domain.CommentAuthor;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.comment.domain.CommentView;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.ModerationMode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class PublicCommentControllerTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");
    private static final String ORIGIN = "https://example.com";
    private static final String PAGE_URL = "https://example.com/blog/post-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private PublicCommentService publicCommentService;

    @MockitoBean
    private DomainPolicyService domainPolicyService;

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

        verifyNoInteractions(currentUserService);
    }

    @Test
    void listCommentsReturnsPaginatedApprovedComments() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        CommentView comment = comment(siteId, pageId, null, CommentStatus.APPROVED);
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(publicCommentService.listComments(siteId, ORIGIN, PAGE_URL, 1, 20))
            .thenReturn(new CommentPage(List.of(comment), 1, 20, 1));

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .param("pageUrl", PAGE_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id", is(comment.id().toString())))
            .andExpect(jsonPath("$.items[0].siteId", is(siteId.toString())))
            .andExpect(jsonPath("$.items[0].pageId", is(pageId.toString())))
            .andExpect(jsonPath("$.items[0].author.email", is("visitor@example.com")))
            .andExpect(jsonPath("$.items[0].content", is("Hello world")))
            .andExpect(jsonPath("$.items[0].status", is("APPROVED")))
            .andExpect(jsonPath("$.items[0].replies", empty()))
            .andExpect(jsonPath("$.page", is(1)))
            .andExpect(jsonPath("$.pageSize", is(20)))
            .andExpect(jsonPath("$.totalItems", is(1)))
            .andExpect(jsonPath("$.totalPages", is(1)));

        verifyNoInteractions(currentUserService);
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
                .header(HttpHeaders.ORIGIN, ORIGIN)
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
    void createCommentReturnsCreatedCommentForAuthenticatedUser() throws Exception {
        UUID siteId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        AuthenticatedUser currentUser = currentUser();
        CommentView created = comment(siteId, pageId, null, CommentStatus.PENDING);
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(publicCommentService.createComment(currentUser, siteId, ORIGIN, PAGE_URL, null, "Hello world"))
            .thenReturn(created);

        mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
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
    }

    @Test
    void preflightForAllowedOriginReturnsCorsHeadersWithoutBearer() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(options("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization, Content-Type, Accept"));

        verifyNoInteractions(currentUserService, publicCommentService);
    }

    @Test
    void preflightForDisallowedOriginDoesNotReturnPermissiveCorsHeaders() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, "https://evil.example")).thenReturn(false);

        mockMvc.perform(options("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

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
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")));

        verifyNoInteractions(currentUserService, publicCommentService);
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "visitor@example.com", Set.of("COMMENTER"), TIMESTAMP, TIMESTAMP);
    }

    private CommentView comment(UUID siteId, UUID pageId, UUID parentId, CommentStatus status) {
        return new CommentView(
            UUID.randomUUID(),
            siteId,
            pageId,
            parentId,
            new CommentAuthor(UUID.randomUUID(), "visitor@example.com", "visitor@example.com"),
            "Hello world",
            status,
            TIMESTAMP,
            TIMESTAMP,
            List.of()
        );
    }
}
