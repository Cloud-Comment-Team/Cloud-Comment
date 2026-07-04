package com.cloudcomment.site.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.application.EmbedCode;
import com.cloudcomment.site.application.SitePage;
import com.cloudcomment.site.application.SiteService;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;
import com.cloudcomment.site.domain.WidgetCornerRadius;
import com.cloudcomment.site.domain.WidgetStyle;
import com.cloudcomment.site.domain.WidgetTheme;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class SiteControllerTests {

    private static final Instant CREATED_AT = Instant.parse("2026-06-28T12:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-28T13:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private SiteService siteService;

    @Test
    void sitesRequireBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/sites"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.message", is("Invalid or expired session")))
            .andExpect(jsonPath("$.error.status", is(401)))
            .andExpect(jsonPath("$.error.path", is("/api/sites")))
            .andExpect(jsonPath("$.error.fields", empty()));

        verifyNoInteractions(siteService);
    }

    @Test
    void listSitesReturnsPaginatedSitesForCurrentUser() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        Site site = site(currentUser.id(), UUID.randomUUID());
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(siteService.listSites(currentUser, 1, 20)).thenReturn(new SitePage(List.of(site), 1, 20, 1));

        mockMvc.perform(get("/api/sites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id", is(site.id().toString())))
            .andExpect(jsonPath("$.items[0].ownerId", is(currentUser.id().toString())))
            .andExpect(jsonPath("$.items[0].name", is("Example site")))
            .andExpect(jsonPath("$.items[0].domain", is("example.com")))
            .andExpect(jsonPath("$.items[0].publicKey", is("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")))
            .andExpect(jsonPath("$.items[0].moderationMode", is("PRE_MODERATION")))
            .andExpect(jsonPath("$.items[0].isActive", is(true)))
            .andExpect(jsonPath("$.items[0].widgetStyle.theme", is("AUTO")))
            .andExpect(jsonPath("$.items[0].widgetStyle.accentColor", is(WidgetStyle.DEFAULT_ACCENT_COLOR)))
            .andExpect(jsonPath("$.items[0].widgetStyle.cornerRadius", is("MEDIUM")))
            .andExpect(jsonPath("$.items[0].autoModeration.enabled", is(true)))
            .andExpect(jsonPath("$.items[0].autoModeration.strictness", is("BALANCED")))
            .andExpect(jsonPath("$.items[0].autoModeration.blockedWords", empty()))
            .andExpect(jsonPath("$.items[0].autoModeration.holdLinks", is(true)))
            .andExpect(jsonPath("$.items[0].autoModeration.blockLinks", is(false)))
            .andExpect(jsonPath("$.items[0].autoModeration.maxLinks", is(2)))
            .andExpect(jsonPath("$.items[0].allowedOrigins", contains("https://example.com")))
            .andExpect(jsonPath("$.page", is(1)))
            .andExpect(jsonPath("$.pageSize", is(20)))
            .andExpect(jsonPath("$.totalItems", is(1)))
            .andExpect(jsonPath("$.totalPages", is(1)));
    }

    @Test
    void listSitesRejectsPageAboveMaximum() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(get("/api/sites")
                .param("page", "100001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/sites")))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());

        verifyNoInteractions(siteService);
    }

    @Test
    void createSiteReturnsCreatedSite() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        Site site = site(currentUser.id(), UUID.randomUUID());
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(siteService.createSite(
            eq(currentUser),
            eq("Example site"),
            eq("Example.COM"),
            eq(ModerationMode.PRE_MODERATION),
            eq(List.of("https://Example.com")),
            isNull(),
            isNull()
        )).thenReturn(site);

        mockMvc.perform(post("/api/sites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Example site",
                      "domain": "Example.COM",
                      "moderationMode": "PRE_MODERATION",
                      "allowedOrigins": ["https://Example.com"]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(site.id().toString())))
            .andExpect(jsonPath("$.ownerId", is(currentUser.id().toString())))
            .andExpect(jsonPath("$.domain", is("example.com")))
            .andExpect(jsonPath("$.isActive", is(true)))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void createSitePassesWidgetStyleAndAutoModerationToService() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        WidgetStyle widgetStyle = new WidgetStyle(WidgetTheme.DARK, "#123abc", WidgetCornerRadius.LARGE);
        AutoModerationSettings autoModeration = new AutoModerationSettings(
            true,
            AutoModerationStrictness.STRICT,
            List.of("casino", "spam"),
            false,
            true,
            0
        );
        Site site = site(currentUser.id(), siteId, widgetStyle, autoModeration);
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(siteService.createSite(
            eq(currentUser),
            eq("Example site"),
            eq("example.com"),
            eq(ModerationMode.POST_MODERATION),
            eq(List.of("https://example.com")),
            eq(widgetStyle),
            eq(autoModeration)
        )).thenReturn(site);

        mockMvc.perform(post("/api/sites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Example site",
                      "domain": "example.com",
                      "moderationMode": "POST_MODERATION",
                      "allowedOrigins": ["https://example.com"],
                      "widgetStyle": {
                        "theme": "DARK",
                        "accentColor": "#123abc",
                        "cornerRadius": "LARGE"
                      },
                      "autoModeration": {
                        "enabled": true,
                        "strictness": "STRICT",
                        "blockedWords": ["casino", "spam"],
                        "holdLinks": false,
                        "blockLinks": true,
                        "maxLinks": 0
                      }
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(siteId.toString())))
            .andExpect(jsonPath("$.widgetStyle.theme", is("DARK")))
            .andExpect(jsonPath("$.widgetStyle.accentColor", is("#123abc")))
            .andExpect(jsonPath("$.widgetStyle.cornerRadius", is("LARGE")))
            .andExpect(jsonPath("$.autoModeration.enabled", is(true)))
            .andExpect(jsonPath("$.autoModeration.strictness", is("STRICT")))
            .andExpect(jsonPath("$.autoModeration.blockedWords", contains("casino", "spam")))
            .andExpect(jsonPath("$.autoModeration.holdLinks", is(false)))
            .andExpect(jsonPath("$.autoModeration.blockLinks", is(true)))
            .andExpect(jsonPath("$.autoModeration.maxLinks", is(0)));
    }

    @Test
    void createSiteRejectsInvalidRequestWithValidationEnvelope() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(post("/api/sites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "",
                      "domain": "https://example.com/path",
                      "moderationMode": "PRE_MODERATION",
                      "allowedOrigins": []
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.message", is("Request validation failed")))
            .andExpect(jsonPath("$.error.status", is(400)))
            .andExpect(jsonPath("$.error.path", is("/api/sites")))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());

        verifyNoInteractions(siteService);
    }

    @Test
    void createSiteRejectsInvalidAutoModerationRequest() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(post("/api/sites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Example site",
                      "domain": "example.com",
                      "moderationMode": "PRE_MODERATION",
                      "allowedOrigins": ["https://example.com"],
                      "autoModeration": {
                        "enabled": true,
                        "strictness": "BALANCED",
                        "blockedWords": ["%s"],
                        "holdLinks": true,
                        "blockLinks": false,
                        "maxLinks": 21
                      }
                    }
                    """.formatted("x".repeat(AutoModerationSettings.MAX_BLOCKED_WORD_LENGTH + 1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/sites")))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());

        verifyNoInteractions(siteService);
    }

    @Test
    void createSiteRejectsTooManyAllowedOrigins() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(post("/api/sites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Example site",
                      "domain": "example.com",
                      "moderationMode": "PRE_MODERATION",
                      "allowedOrigins": [%s]
                    }
                    """.formatted(tooManyOriginsJson())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/sites")))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());

        verifyNoInteractions(siteService);
    }

    @Test
    void createSiteReturnsConflictWhenOwnerAlreadyHasDomain() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(siteService.createSite(
            eq(currentUser),
            eq("Example site"),
            eq("example.com"),
            eq(ModerationMode.PRE_MODERATION),
            eq(List.of("https://example.com")),
            isNull(),
            isNull()
        )).thenThrow(new ApplicationException(ApiErrorCode.BUSINESS_ERROR, "Site domain already exists"));

        mockMvc.perform(post("/api/sites")
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Example site",
                      "domain": "example.com",
                      "moderationMode": "PRE_MODERATION",
                      "allowedOrigins": ["https://example.com"]
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("BUSINESS_ERROR")))
            .andExpect(jsonPath("$.error.message", is("Site domain already exists")))
            .andExpect(jsonPath("$.error.status", is(409)))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void getSiteReturnsNotFoundForForeignOrMissingSite() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(siteService.getSite(currentUser, siteId))
            .thenThrow(new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));

        mockMvc.perform(get("/api/sites/{siteId}", siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")))
            .andExpect(jsonPath("$.error.status", is(404)))
            .andExpect(jsonPath("$.error.path", is("/api/sites/" + siteId)))
            .andExpect(jsonPath("$.error.fields", empty()));
    }

    @Test
    void updateSitePassesPatchFieldsToService() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        Site updatedSite = new Site(
            siteId,
            currentUser.id(),
            "Updated site",
            "updated.example.com",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ModerationMode.POST_MODERATION,
            false,
            List.of("https://updated.example.com"),
            CREATED_AT,
            UPDATED_AT
        );
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(siteService.updateSite(
            currentUser,
            siteId,
            "Updated site",
            "updated.example.com",
            ModerationMode.POST_MODERATION,
            false,
            null,
            null
        )).thenReturn(updatedSite);

        mockMvc.perform(patch("/api/sites/{siteId}", siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Updated site",
                      "domain": "updated.example.com",
                      "moderationMode": "POST_MODERATION",
                      "isActive": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("Updated site")))
            .andExpect(jsonPath("$.domain", is("updated.example.com")))
            .andExpect(jsonPath("$.moderationMode", is("POST_MODERATION")))
            .andExpect(jsonPath("$.isActive", is(false)));
    }

    @Test
    void updateSitePassesWidgetStyleAndAutoModerationPatchToService() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        WidgetStyle widgetStyle = new WidgetStyle(WidgetTheme.LIGHT, "#00ffaa", WidgetCornerRadius.SMALL);
        AutoModerationSettings autoModeration = new AutoModerationSettings(
            false,
            AutoModerationStrictness.BALANCED,
            List.of(),
            false,
            false,
            2
        );
        Site updatedSite = site(currentUser.id(), siteId, widgetStyle, autoModeration);
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(siteService.updateSite(
            currentUser,
            siteId,
            null,
            null,
            null,
            null,
            widgetStyle,
            autoModeration
        )).thenReturn(updatedSite);

        mockMvc.perform(patch("/api/sites/{siteId}", siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "widgetStyle": {
                        "theme": "LIGHT",
                        "accentColor": "#00ffaa",
                        "cornerRadius": "SMALL"
                      },
                      "autoModeration": {
                        "enabled": false,
                        "holdLinks": false
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.widgetStyle.theme", is("LIGHT")))
            .andExpect(jsonPath("$.widgetStyle.accentColor", is("#00ffaa")))
            .andExpect(jsonPath("$.widgetStyle.cornerRadius", is("SMALL")))
            .andExpect(jsonPath("$.autoModeration.enabled", is(false)))
            .andExpect(jsonPath("$.autoModeration.holdLinks", is(false)));
    }

    @Test
    void replaceAllowedOriginsReturnsUpdatedSite() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        Site updatedSite = new Site(
            siteId,
            currentUser.id(),
            "Example site",
            "example.com",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ModerationMode.PRE_MODERATION,
            true,
            List.of("https://admin.example.com"),
            CREATED_AT,
            UPDATED_AT
        );
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(siteService.replaceAllowedOrigins(currentUser, siteId, List.of("https://admin.example.com")))
            .thenReturn(updatedSite);

        mockMvc.perform(put("/api/sites/{siteId}/allowed-origins", siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "allowedOrigins": ["https://admin.example.com"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowedOrigins", contains("https://admin.example.com")));
    }

    @Test
    void replaceAllowedOriginsRejectsTooManyAllowedOrigins() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(put("/api/sites/{siteId}/allowed-origins", siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "allowedOrigins": [%s]
                    }
                    """.formatted(tooManyOriginsJson())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/sites/" + siteId + "/allowed-origins")))
            .andExpect(jsonPath("$.error.fields").isNotEmpty());

        verifyNoInteractions(siteService);
    }

    @Test
    void getEmbedCodeReturnsSnippetAndDataAttributes() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        when(siteService.getEmbedCode(currentUser, siteId)).thenReturn(new EmbedCode(
            siteId,
            "http://localhost/widget/cloud-comment-widget.js",
            "<script src=\"http://localhost/widget/cloud-comment-widget.js\" data-site-id=\"%s\" data-api-base-url=\"http://localhost/api\"></script>".formatted(siteId),
            Map.of(
                "siteId", siteId.toString(),
                "apiBaseUrl", "http://localhost/api"
            )
        ));

        mockMvc.perform(get("/api/sites/{siteId}/embed-code", siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.siteId", is(siteId.toString())))
            .andExpect(jsonPath("$.scriptUrl", is("http://localhost/widget/cloud-comment-widget.js")))
            .andExpect(jsonPath("$.embedCode", is("<script src=\"http://localhost/widget/cloud-comment-widget.js\" data-site-id=\"" + siteId + "\" data-api-base-url=\"http://localhost/api\"></script>")))
            .andExpect(jsonPath("$.dataAttributes.siteId", is(siteId.toString())))
            .andExpect(jsonPath("$.dataAttributes.apiBaseUrl", is("http://localhost/api")));

        verify(siteService).getEmbedCode(currentUser, siteId);
    }

    @Test
    void deleteSiteReturnsNoContent() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);

        mockMvc.perform(delete("/api/sites/{siteId}", siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isNoContent());

        verify(siteService).deleteSite(currentUser, siteId);
    }

    @Test
    void deleteSiteReturnsNotFoundForForeignOrMissingSite() throws Exception {
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();
        when(currentUserService.getCurrentUser(eq("plain-session-token"))).thenReturn(currentUser);
        org.mockito.Mockito.doThrow(new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"))
            .when(siteService)
            .deleteSite(currentUser, siteId);

        mockMvc.perform(delete("/api/sites/{siteId}", siteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.path", is("/api/sites/" + siteId)));
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), CREATED_AT, UPDATED_AT);
    }

    private Site site(UUID ownerId, UUID siteId) {
        return site(ownerId, siteId, WidgetStyle.defaultStyle(), AutoModerationSettings.defaultSettings());
    }

    private Site site(UUID ownerId, UUID siteId, WidgetStyle widgetStyle, AutoModerationSettings autoModeration) {
        return new Site(
            siteId,
            ownerId,
            "Example site",
            "example.com",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ModerationMode.PRE_MODERATION,
            true,
            widgetStyle,
            autoModeration,
            List.of("https://example.com"),
            CREATED_AT,
            UPDATED_AT
        );
    }

    private String tooManyOriginsJson() {
        return IntStream.rangeClosed(1, 21)
            .mapToObj(index -> "\"https://origin-" + index + ".example.com\"")
            .collect(Collectors.joining(", "));
    }
}
