package com.cloudcomment.widgetcontext.api;

import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.widgetcontext.application.WidgetBootstrapResult;
import com.cloudcomment.widgetcontext.application.WidgetContextService;
import com.cloudcomment.widgetcontext.application.WidgetFrameContextResult;
import com.cloudcomment.widgetcontext.application.WidgetFramePageService;
import com.cloudcomment.widgetcontext.application.WidgetSecurityRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "cloud-comment.embed.api-base-url=https://api.example.net/api",
    "cloud-comment.embed.widget-base-url=https://widget.example.net",
    "cloud-comment.security.trusted-proxies=10.0.0.0/8,127.0.0.1/32,::1/128"
})
@AutoConfigureMockMvc
class WidgetContextControllerTests {

    private static final String EMBEDDING_ORIGIN = "https://card.example.com";
    private static final String FRAME_ORIGIN = "https://widget.example.net";
    private static final String PAGE_URL = EMBEDDING_ORIGIN + "/article";
    private static final Instant NOW = Instant.parse("2026-07-13T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WidgetContextService widgetContextService;

    @MockitoBean
    private WidgetFramePageService framePageService;

    @MockitoBean
    private WidgetSecurityRateLimiter rateLimiter;

    @MockitoBean
    private DomainPolicyService domainPolicyService;

    @Test
    void bootstrapUsesEmbeddingCorsReturnsTicketAndIsNeverCached() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, EMBEDDING_ORIGIN)).thenReturn(true);
        when(widgetContextService.bootstrap(siteId, EMBEDDING_ORIGIN, PAGE_URL, "public-key"))
            .thenReturn(new WidgetBootstrapResult(
                "bootstrap-ticket",
                NOW.plusSeconds(120),
                PAGE_URL,
                "A".repeat(43)
            ));

        mockMvc.perform(post("/api/public/sites/{siteId}/widget-context/bootstrap", siteId)
                .header(HttpHeaders.ORIGIN, EMBEDDING_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.25, 10.1.2.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"publicKey":"public-key","pageUrl":"%s"}
                    """.formatted(PAGE_URL))
                .with(request -> {
                    request.setRemoteAddr("10.2.3.4");
                    return request;
                }))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EMBEDDING_ORIGIN))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
            .andExpect(jsonPath("$.ticket", is("bootstrap-ticket")))
            .andExpect(jsonPath("$.expiresAt", is("2026-07-13T08:02:00Z")))
            .andExpect(jsonPath("$.canonicalPageUrl", is(PAGE_URL)))
            .andExpect(jsonPath("$.publicKeyFingerprint", is("A".repeat(43))));

        verify(rateLimiter).checkBootstrap(siteId, EMBEDDING_ORIGIN, "203.0.113.25");
    }

    @Test
    void exchangeUsesDedicatedFrameCorsReturnsContextAndIsNeverCached() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(widgetContextService.acceptsFrameOrigin(FRAME_ORIGIN)).thenReturn(true);
        when(widgetContextService.exchange(siteId, FRAME_ORIGIN, "bootstrap-ticket", "proof"))
            .thenReturn(new WidgetFrameContextResult("context-token", NOW.plusSeconds(7200)));

        mockMvc.perform(post("/api/public/sites/{siteId}/widget-context/exchange", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header("X-Forwarded-For", "203.0.113.99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ticket":"bootstrap-ticket","proof":"proof"}
                    """)
                .with(request -> {
                    request.setRemoteAddr("198.51.100.8");
                    return request;
                }))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
            .andExpect(jsonPath("$.contextToken", is("context-token")))
            .andExpect(jsonPath("$.expiresAt", is("2026-07-13T10:00:00Z")));

        verify(rateLimiter).checkExchange(siteId, FRAME_ORIGIN, "198.51.100.8");
    }

    @Test
    void exchangeFromOpaqueOrUnconfiguredOriginIsRejectedBeforeControllerInDedicatedMode() throws Exception {
        UUID siteId = UUID.randomUUID();

        mockMvc.perform(post("/api/public/sites/{siteId}/widget-context/exchange", siteId)
                .header(HttpHeaders.ORIGIN, "null")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ticket":"bootstrap-ticket","proof":"proof"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_BOOTSTRAP")));

        verifyNoInteractions(rateLimiter);
    }

    @Test
    void rateLimitErrorIs429AndNeverCached() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, EMBEDDING_ORIGIN)).thenReturn(true);
        doThrow(new ApplicationException(ApiErrorCode.RATE_LIMITED, "Too many requests"))
            .when(rateLimiter).checkBootstrap(eq(siteId), eq(EMBEDDING_ORIGIN), anyString());

        mockMvc.perform(post("/api/public/sites/{siteId}/widget-context/bootstrap", siteId)
                .header(HttpHeaders.ORIGIN, EMBEDDING_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"publicKey":"public-key","pageUrl":"%s"}
                    """.formatted(PAGE_URL)))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("RATE_LIMITED")));
    }

    @Test
    void dynamicFrameShellHasStrictSecurityHeadersAndNoInlineScript() throws Exception {
        UUID siteId = UUID.randomUUID();
        String csp = "default-src 'none'; script-src 'self' https://widget.example.net; "
            + "connect-src 'self' https://widget.example.net; style-src 'self' 'unsafe-inline'; img-src data:; "
            + "base-uri 'none'; form-action 'none'; object-src 'none'; frame-ancestors https://card.example.com";
        String html = "<html><body><script src=\"/widget/cloud-comment-widget-frame.js\" defer></script></body></html>";
        when(framePageService.build(siteId)).thenReturn(new WidgetFramePageService.WidgetFramePage(html, csp));

        mockMvc.perform(get("/api/public/sites/{siteId}/widget-frame", siteId))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string("Content-Security-Policy", csp))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().doesNotExist("X-Frame-Options"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(html));
    }

    @Test
    void missingFrameShellIsNotFoundAndNeverCached() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(framePageService.build(siteId)).thenThrow(
            new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found")
        );

        mockMvc.perform(get("/api/public/sites/{siteId}/widget-frame", siteId))
            .andExpect(status().isNotFound())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().doesNotExist("X-Frame-Options"))
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void nonFrameRoutesKeepDefaultClickjackingProtection() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
