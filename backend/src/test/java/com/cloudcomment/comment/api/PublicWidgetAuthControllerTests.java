package com.cloudcomment.comment.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.auth.application.LoginResult;
import com.cloudcomment.auth.application.LoginService;
import com.cloudcomment.auth.application.LogoutService;
import com.cloudcomment.auth.application.RegisteredUser;
import com.cloudcomment.auth.application.RegistrationService;
import com.cloudcomment.privacy.application.ConsentTestSupport;
import com.cloudcomment.privacy.application.RegistrationConsent;
import com.cloudcomment.privacy.domain.ConsentSource;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.widgetcontext.application.ResolvedWidgetContext;
import com.cloudcomment.widgetcontext.application.WidgetContextService;
import com.cloudcomment.widgetcontext.application.WidgetSecurityRateLimiter;
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
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PublicWidgetAuthControllerTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");
    private static final String ORIGIN = "https://example.com";
    private static final String FRAME_ORIGIN = "https://widget.example.net";
    private static final String EMAIL = "visitor@example.com";
    private static final String PASSWORD = "Password123!";
    private static final String CONTEXT_TOKEN = "frame-context-token";
    private static final UUID CONTEXT_ID = UUID.fromString("70753d61-fc78-4cab-8969-d59cc57d6a6e");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private DomainPolicyService domainPolicyService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private LogoutService logoutService;

    @MockitoBean
    private WidgetContextService widgetContextService;

    @MockitoBean
    private WidgetSecurityRateLimiter rateLimiter;

    @BeforeEach
    void setUpWidgetContext() {
        when(widgetContextService.acceptsFrameOrigin(FRAME_ORIGIN)).thenReturn(true);
        when(widgetContextService.resolve(any(UUID.class), eq(CONTEXT_TOKEN))).thenAnswer(invocation -> {
            UUID siteId = invocation.getArgument(0);
            return new ResolvedWidgetContext(
                CONTEXT_ID,
                siteId,
                ORIGIN,
                "a".repeat(64),
                TIMESTAMP.plusSeconds(7200)
            );
        });
    }

    @Test
    void widgetAuthPreflightUsesSiteScopedCorsPolicyWithoutBearer() throws Exception {
        UUID siteId = UUID.randomUUID();
        mockMvc.perform(options("/api/public/sites/{siteId}/auth/login", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    "Content-Type, " + WidgetContextService.CONTEXT_HEADER
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
            ))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        verifyNoInteractions(currentUserService, registrationService, loginService);
    }

    @Test
    void widgetRegisterRequiresAllowedSiteOriginAndReturnsCreatedUser() throws Exception {
        UUID siteId = UUID.randomUUID();
        RegisteredUser user = new RegisteredUser(
            UUID.randomUUID(),
            EMAIL,
            Set.of("COMMENTER"),
            TIMESTAMP,
            TIMESTAMP
        );
        when(registrationService.register(
            eq(EMAIL),
            eq(PASSWORD),
            any(RegistrationConsent.class),
            eq(ConsentSource.WIDGET)
        )).thenReturn(user);

        mockMvc.perform(post("/api/public/sites/{siteId}/auth/register", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-Forwarded-For", "203.0.113.30, 10.5.0.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ConsentTestSupport.registerRequestJson(EMAIL, PASSWORD))
                .with(requestBuilder -> {
                    requestBuilder.setRemoteAddr("10.9.0.4");
                    return requestBuilder;
                }))
            .andExpect(status().isCreated())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.id", is(user.id().toString())))
            .andExpect(jsonPath("$.email", is(EMAIL)))
            .andExpect(jsonPath("$.roles[0]", is("COMMENTER")));

        verify(domainPolicyService).validate(siteId, ORIGIN);
        verify(rateLimiter).checkRegister(siteId, ORIGIN, "203.0.113.30", EMAIL);
        verify(registrationService).register(
            eq(EMAIL),
            eq(PASSWORD),
            any(RegistrationConsent.class),
            eq(ConsentSource.WIDGET)
        );
        verifyNoInteractions(currentUserService, loginService);
    }

    @Test
    void widgetLoginRequiresAllowedSiteOriginAndReturnsSessionToken() throws Exception {
        UUID siteId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(
            UUID.randomUUID(),
            EMAIL,
            Set.of("COMMENTER"),
            TIMESTAMP,
            TIMESTAMP
        );
        when(loginService.loginWidget(EMAIL, PASSWORD, siteId, ORIGIN))
            .thenReturn(new LoginResult("plain-session-token", "Bearer", TIMESTAMP.plusSeconds(3600), user));

        mockMvc.perform(post("/api/public/sites/{siteId}/auth/login", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header("X-Forwarded-For", "203.0.113.31, 10.5.0.9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMAIL, PASSWORD))
                .with(requestBuilder -> {
                    requestBuilder.setRemoteAddr("10.9.0.4");
                    return requestBuilder;
                }))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.token", is("plain-session-token")))
            .andExpect(jsonPath("$.tokenType", is("Bearer")))
            .andExpect(jsonPath("$.user.email", is(EMAIL)));

        verify(domainPolicyService).validate(siteId, ORIGIN);
        verify(rateLimiter).checkLogin(siteId, ORIGIN, "203.0.113.31", EMAIL);
        verify(loginService).loginWidget(EMAIL, PASSWORD, siteId, ORIGIN);
        verifyNoInteractions(currentUserService, registrationService);
    }

    @Test
    void widgetMeReturnsCurrentUserForAllowedOriginAndBearerToken() throws Exception {
        UUID siteId = UUID.randomUUID();
        AuthenticatedUser user = currentUser();
        when(currentUserService.getWidgetCurrentUser(
            "plain-session-token",
            siteId,
            ORIGIN
        )).thenReturn(user);

        mockMvc.perform(get("/api/public/sites/{siteId}/auth/me", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.id", is(user.id().toString())))
            .andExpect(jsonPath("$.email", is(user.email())));

        verify(domainPolicyService).validate(siteId, ORIGIN);
        verifyNoInteractions(registrationService, loginService, logoutService);
    }

    @Test
    void widgetLogoutRevokesSessionThroughSiteScopedCorsRoute() throws Exception {
        UUID siteId = UUID.randomUUID();
        doNothing().when(logoutService).logoutWidget(
            "plain-session-token",
            siteId,
            ORIGIN
        );

        mockMvc.perform(post("/api/public/sites/{siteId}/auth/logout", siteId)
                .header(HttpHeaders.ORIGIN, FRAME_ORIGIN)
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plain-session-token"))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRAME_ORIGIN))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        verify(logoutService).logoutWidget(
            "plain-session-token",
            siteId,
            ORIGIN
        );
        verifyNoInteractions(currentUserService, registrationService, loginService);
    }

    @Test
    void widgetAuthWithWrongFrameOriginIsRejectedBeforeController() throws Exception {
        UUID siteId = UUID.randomUUID();

        mockMvc.perform(post("/api/public/sites/{siteId}/auth/login", siteId)
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(WidgetContextService.CONTEXT_HEADER, CONTEXT_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.error.code", is("INVALID_WIDGET_CONTEXT")))
            .andExpect(jsonPath("$.error.message", is("Invalid widget context")))
            .andExpect(jsonPath("$.error.fields", empty()));

        verifyNoInteractions(currentUserService, registrationService, loginService);
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(
            UUID.randomUUID(),
            EMAIL,
            Set.of("COMMENTER"),
            TIMESTAMP,
            TIMESTAMP
        );
    }
}
