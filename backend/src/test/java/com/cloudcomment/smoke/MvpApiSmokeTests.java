package com.cloudcomment.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockCookie;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.cloudcomment.widgetcontext.application.WidgetContextService;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.cloudcomment.privacy.application.ConsentTestSupport.registerRequestJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminRequest;
import static com.cloudcomment.support.AdminSecurityTestSupport.adminSession;
import static com.cloudcomment.support.AdminSecurityTestSupport.csrf;

/** Acceptance smoke tests for implemented MVP endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MvpApiSmokeTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mvpHealthAndAuthSmokeChecklist() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("UP")))
            .andExpect(jsonPath("$.application", is("cloud-comment")));

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-an-email",
                      "password": "short"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.path", is("/api/auth/register")))
            .andExpect(jsonPath("$.error.fields").isArray());

        String email = "smoke-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(email, "strong-password")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email", is(email)))
            .andExpect(jsonPath("$.roles", contains("COMMENTER")));

        LoginSession loginSession = adminLogin(email, "strong-password");
        String token = loginSession.token();
        assertThat(loginSession.body()).contains("\"email\":\"" + email + "\"");
        assertThat(loginSession.body()).doesNotContain("\"token\"").doesNotContain("tokenType");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/auth/me")
                .with(adminRequest(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is(email)));

        String siteDomain = "smoke-%s.example.com".formatted(email.hashCode());
        String origin = "https://" + siteDomain;
        String pageUrl = origin + "/blog/post-1";

        String siteResponse = mockMvc.perform(post("/api/sites")
                .with(adminRequest(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Smoke site",
                      "domain": "%s",
                      "moderationMode": "POST_MODERATION",
                      "allowedOrigins": ["%s"]
                    }
                    """.formatted(siteDomain, origin)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is("Smoke site")))
            .andExpect(jsonPath("$.moderationMode", is("POST_MODERATION")))
            .andExpect(jsonPath("$.isActive", is(true)))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String siteId = extractString(siteResponse, "id");
        String publicKey = extractString(siteResponse, "publicKey");
        assertThat(siteId).isNotBlank();
        assertThat(publicKey).matches("[0-9a-f]{64}");

        mockMvc.perform(get("/api/sites/{siteId}/embed-code", siteId)
                .with(adminRequest(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.siteId", is(siteId)))
            .andExpect(jsonPath("$.scriptUrl", is("http://localhost/widget/cloud-comment-widget.js")))
            .andExpect(jsonPath("$.embedCode", is("<script src=\"http://localhost/widget/cloud-comment-widget.js\" data-site-id=\"" + siteId + "\" data-api-base-url=\"http://localhost/api\" data-frame-base-url=\"http://widget.localhost\"></script>")))
            .andExpect(jsonPath("$.dataAttributes.siteId", is(siteId)))
            .andExpect(jsonPath("$.dataAttributes.apiBaseUrl", is("http://localhost/api")))
            .andExpect(jsonPath("$.dataAttributes.frameBaseUrl", is("http://widget.localhost")));

        mockMvc.perform(get("/api/public/sites/{siteId}/config", siteId)
                .header(HttpHeaders.ORIGIN, origin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.siteId", is(siteId)))
            .andExpect(jsonPath("$.moderationMode", is("POST_MODERATION")));

        mockMvc.perform(get("/api/sites/{siteId}/installation-status", siteId)
                .with(adminRequest(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("HEALTHY")))
            .andExpect(jsonPath("$.reason", is("RECENT_SUCCESS")))
            .andExpect(jsonPath("$.lastSuccessfulOrigin", is(origin)))
            .andExpect(jsonPath("$.firstCommentReceived", is(false)));

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, origin)
                .param("pageUrl", pageUrl))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", empty()))
            .andExpect(jsonPath("$.totalItems", is(0)));

        WidgetSession widgetSession = widgetLogin(siteId, origin, pageUrl, email, "strong-password");

        String commentResponse = mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, "http://widget.localhost")
                .header(WidgetContextService.CONTEXT_HEADER, widgetSession.contextToken())
                .header("X-CloudComment-Page-Url", pageUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + widgetSession.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageUrl": "%s",
                      "content": "Smoke comment"
                    }
                    """.formatted(pageUrl)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.siteId", is(siteId)))
            .andExpect(jsonPath("$.content", is("Smoke comment")))
            .andExpect(jsonPath("$.status", is("APPROVED")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String commentId = extractString(commentResponse, "id");

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, origin)
                .param("pageUrl", pageUrl))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id", is(commentId)))
            .andExpect(jsonPath("$.items[0].content", is("Smoke comment")))
            .andExpect(jsonPath("$.items[0].status", is("APPROVED")))
            .andExpect(jsonPath("$.totalItems", is(1)));

        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(otherEmail, "strong-password")))
            .andExpect(status().isCreated());
        String otherToken = adminLogin(otherEmail, "strong-password").token();

        mockMvc.perform(get("/api/sites/{siteId}/installation-status", siteId)
                .with(adminRequest(otherToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")));
        mockMvc.perform(get("/api/sites/{siteId}/installation-status", UUID.randomUUID())
                .with(adminRequest(otherToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")));

        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")))
            .andExpect(jsonPath("$.error.path", is("/api/auth/me")))
            .andExpect(jsonPath("$.error.fields", empty()));

        mockMvc.perform(post("/api/auth/logout")
                .with(adminRequest(token)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                .with(adminSession(token)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_SESSION")));
    }

    @Test
    void canonicalPageUrlPolicyUsesOneDatabaseThreadAndKeepsFunctionalQueriesSeparate() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "canonical-" + suffix + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestJson(email, "strong-password")))
            .andExpect(status().isCreated());

        String token = adminLogin(email, "strong-password").token();

        String siteDomain = "canonical-" + suffix + ".example.com";
        String origin = "https://" + siteDomain;
        String siteResponse = mockMvc.perform(post("/api/sites")
                .with(adminRequest(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Canonical URL smoke",
                      "domain": "%s",
                      "moderationMode": "POST_MODERATION",
                      "allowedOrigins": ["%s"]
                    }
                    """.formatted(siteDomain, origin)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String siteId = extractString(siteResponse, "id");
        String trackingGetUrl = origin + "/article?tab=comments&%67braid=first&srsltid=result#thread";
        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, origin)
                .param("pageUrl", trackingGetUrl))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", empty()));

        String trackingPostUrl = origin + "/article?wbraid=second&tab=comments&gad_source=1"
            + "&gad_campaignid=2&client_secret=secret&x%2Damz%2Dsignature=signed#comments";
        WidgetSession trackingSession = widgetLogin(
            siteId,
            origin,
            trackingGetUrl,
            email,
            "strong-password"
        );
        String commentResponse = mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, "http://widget.localhost")
                .header(WidgetContextService.CONTEXT_HEADER, trackingSession.contextToken())
                .header("X-CloudComment-Page-Url", trackingPostUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + trackingSession.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageUrl": "%s",
                      "content": "Canonical thread comment"
                    }
                    """.formatted(trackingPostUrl)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String canonicalCommentId = extractString(commentResponse, "id");

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, origin)
                .param("pageUrl", trackingGetUrl))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id", is(canonicalCommentId)))
            .andExpect(jsonPath("$.totalItems", is(1)));

        String functionalPageUrl = origin + "/article?tab=popular&x-goog-signature=secret&gbraid=third";
        WidgetSession functionalSession = widgetLogin(
            siteId,
            origin,
            functionalPageUrl,
            email,
            "strong-password"
        );
        String functionalCommentResponse = mockMvc.perform(post("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, "http://widget.localhost")
                .header(WidgetContextService.CONTEXT_HEADER, functionalSession.contextToken())
                .header("X-CloudComment-Page-Url", functionalPageUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + functionalSession.bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageUrl": "%s",
                      "content": "Functional thread comment"
                    }
                    """.formatted(functionalPageUrl)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String functionalCommentId = extractString(functionalCommentResponse, "id");

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, origin)
                .param("pageUrl", origin + "/article?tab=popular"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id", is(functionalCommentId)))
            .andExpect(jsonPath("$.totalItems", is(1)));

        mockMvc.perform(get("/api/public/sites/{siteId}/pages/comments", siteId)
                .header(HttpHeaders.ORIGIN, origin)
                .param("pageUrl", "https://foreign.example.com/article?tab=comments&x-goog-signature=secret"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")));

        assertThat(jdbcTemplate.queryForList(
            "select url from pages where site_id = ? order by url",
            String.class,
            UUID.fromString(siteId)
        )).containsExactly(
            origin + "/article?tab=comments",
            origin + "/article?tab=popular"
        );
    }

    private String extractToken(String json) {
        return extractString(json, "token");
    }

    private LoginSession adminLogin(String email, String password) throws Exception {
        var response = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return new LoginSession(response.getContentAsString(), MockCookie.parse(setCookie).getValue());
    }

    private WidgetSession widgetLogin(
        String siteId,
        String origin,
        String pageUrl,
        String email,
        String password
    ) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        String encodedPublicKey = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(keyPair.getPublic().getEncoded());
        String bootstrapResponse = mockMvc.perform(post(
                "/api/public/sites/{siteId}/widget-context/bootstrap",
                siteId
            )
                .header(HttpHeaders.ORIGIN, origin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"publicKey":"%s","pageUrl":"%s"}
                    """.formatted(encodedPublicKey, pageUrl)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String ticket = extractString(bootstrapResponse, "ticket");
        String canonicalPageUrl = extractString(bootstrapResponse, "canonicalPageUrl");
        String fingerprint = extractString(bootstrapResponse, "publicKeyFingerprint");
        String payload = "CLOUDCOMMENT_WIDGET_BOOTSTRAP_V1\n" + siteId + "\n" + origin + "\n"
            + canonicalPageUrl + "\n" + fingerprint + "\n" + ticket;
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        String proof = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        String exchangeResponse = mockMvc.perform(post(
                "/api/public/sites/{siteId}/widget-context/exchange",
                siteId
            )
                .header(HttpHeaders.ORIGIN, "http://widget.localhost")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ticket":"%s","proof":"%s"}
                    """.formatted(ticket, proof)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String contextToken = extractString(exchangeResponse, "contextToken");
        String loginResponse = mockMvc.perform(post("/api/public/sites/{siteId}/auth/login", siteId)
                .header(HttpHeaders.ORIGIN, "http://widget.localhost")
                .header(WidgetContextService.CONTEXT_HEADER, contextToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return new WidgetSession(contextToken, extractToken(loginResponse));
    }

    private record LoginSession(String body, String token) {
    }

    private record WidgetSession(String contextToken, String bearerToken) {
    }

    private String extractString(String json, String fieldName) {
        Matcher matcher = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
