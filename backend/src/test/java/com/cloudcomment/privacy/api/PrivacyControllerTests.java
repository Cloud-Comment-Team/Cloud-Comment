package com.cloudcomment.privacy.api;

import com.cloudcomment.privacy.application.ConsentRequirements;
import com.cloudcomment.privacy.application.ConsentService;
import com.cloudcomment.widgetcontext.application.ResolvedWidgetContext;
import com.cloudcomment.widgetcontext.application.WidgetContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "cloud-comment.embed.api-base-url=https://api.example.net/api",
    "cloud-comment.embed.widget-base-url=https://widget.example.net"
})
@AutoConfigureMockMvc
class PrivacyControllerTests {

    private static final String EXTERNAL_ORIGIN = "https://card.ifbest.org";
    private static final String FRAME_ORIGIN = "https://widget.example.net";
    private static final String CONTEXT_TOKEN = "frame-context-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentService consentService;

    @MockitoBean
    private WidgetContextService widgetContextService;

    @BeforeEach
    void setUp() {
        when(consentService.currentRequirements()).thenReturn(requirements());
        when(widgetContextService.acceptsFrameOrigin(FRAME_ORIGIN)).thenReturn(true);
        when(widgetContextService.acceptsContextTransport(null, "widget.example.net", true)).thenReturn(true);
        when(widgetContextService.resolve(any(UUID.class), eq(CONTEXT_TOKEN))).thenAnswer(invocation ->
            new ResolvedWidgetContext(
                UUID.fromString("287e2074-2c86-465c-a868-9e96590302eb"),
                invocation.getArgument(0),
                EXTERNAL_ORIGIN,
                "a".repeat(64),
                Instant.parse("2026-07-13T10:00:00Z")
            )
        );
    }

    @Test
    void consentRequirementsArePublic() throws Exception {
        mockMvc.perform(get("/api/privacy/consent-requirements")
            .header(HttpHeaders.ORIGIN, EXTERNAL_ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
            .andExpect(jsonPath("$.privacyPolicyVersion", is("2026-07-12")))
            .andExpect(jsonPath("$.termsVersion", is("2026-07-01")))
            .andExpect(jsonPath("$.privacyPolicyUrl", is("/legal/privacy-policy.html")))
            .andExpect(jsonPath("$.personalDataNoticeUrl", is("/legal/personal-data-notice.html")))
            .andExpect(jsonPath("$.dataExportInfoUrl", is("/legal/personal-data-notice.html#data-export")));
    }

    @Test
    void globalConsentRequirementsKeepLegacyGetOnlyCrossOriginCompatibility() throws Exception {
        mockMvc.perform(options("/api/privacy/consent-requirements")
                .header(HttpHeaders.ORIGIN, EXTERNAL_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Accept"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void siteAliasRequiresContextAndUsesFrameCorsWithoutCredentials() throws Exception {
        UUID siteId = UUID.randomUUID();

        mockMvc.perform(get("/api/public/sites/{siteId}/privacy/consent-requirements", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.privacyPolicyVersion", is("2026-07-12")));
    }

    @Test
    void siteAliasWithoutContextIsRejectedAndNeverCached() throws Exception {
        UUID siteId = UUID.randomUUID();

        mockMvc.perform(get("/api/public/sites/{siteId}/privacy/consent-requirements", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));
    }

    @Test
    void dedicatedSafeGetAllowsMissingOriginOnlyOnConfiguredWidgetHost() throws Exception {
        UUID siteId = UUID.randomUUID();

        mockMvc.perform(get("/api/public/sites/{siteId}/privacy/consent-requirements", siteId)
                .header(HttpHeaders.HOST, "widget.example.net")
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        mockMvc.perform(get("/api/public/sites/{siteId}/privacy/consent-requirements", siteId)
                .header(HttpHeaders.HOST, "api.example.net")
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));

        mockMvc.perform(get("/api/public/sites/{siteId}/privacy/consent-requirements", siteId)
                .header(HttpHeaders.HOST, "widget.example.net")
                .header(HttpHeaders.ORIGIN, "null")
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")));
    }

    private ConsentRequirements requirements() {
        return new ConsentRequirements(
            "2026-07-12",
            "2026-07-01",
            "/legal/privacy-policy.html",
            "/legal/terms.html",
            "/legal/personal-data-notice.html",
            "/legal/personal-data-notice.html#data-export"
        );
    }
}
