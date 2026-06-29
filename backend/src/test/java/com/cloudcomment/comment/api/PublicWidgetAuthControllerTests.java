package com.cloudcomment.comment.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.CurrentUserService;
import com.cloudcomment.auth.application.LoginResult;
import com.cloudcomment.auth.application.LoginService;
import com.cloudcomment.auth.application.RegisteredUser;
import com.cloudcomment.auth.application.RegistrationService;
import com.cloudcomment.comment.application.DomainPolicyService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class PublicWidgetAuthControllerTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");
    private static final String ORIGIN = "https://example.com";
    private static final String EMAIL = "visitor@example.com";
    private static final String PASSWORD = "Password123!";

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

    @Test
    void widgetAuthPreflightUsesSiteScopedCorsPolicyWithoutBearer() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);

        mockMvc.perform(options("/api/public/sites/{siteId}/auth/login", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization, Content-Type, Accept"));

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
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(registrationService.register(EMAIL, PASSWORD)).thenReturn(user);

        mockMvc.perform(post("/api/public/sites/{siteId}/auth/register", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(jsonPath("$.id", is(user.id().toString())))
            .andExpect(jsonPath("$.email", is(EMAIL)))
            .andExpect(jsonPath("$.roles[0]", is("COMMENTER")));

        verify(domainPolicyService).validate(siteId, ORIGIN);
        verify(registrationService).register(EMAIL, PASSWORD);
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
        when(domainPolicyService.isOriginAllowed(siteId, ORIGIN)).thenReturn(true);
        when(loginService.login(EMAIL, PASSWORD))
            .thenReturn(new LoginResult("plain-session-token", "Bearer", TIMESTAMP.plusSeconds(3600), user));

        mockMvc.perform(post("/api/public/sites/{siteId}/auth/login", siteId)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(jsonPath("$.token", is("plain-session-token")))
            .andExpect(jsonPath("$.tokenType", is("Bearer")))
            .andExpect(jsonPath("$.user.email", is(EMAIL)));

        verify(domainPolicyService).validate(siteId, ORIGIN);
        verify(loginService).login(EMAIL, PASSWORD);
        verifyNoInteractions(currentUserService, registrationService);
    }

    @Test
    void widgetAuthWithoutAllowedOriginIsMaskedBeforeController() throws Exception {
        UUID siteId = UUID.randomUUID();
        when(domainPolicyService.isOriginAllowed(siteId, "https://evil.example")).thenReturn(false);

        mockMvc.perform(post("/api/public/sites/{siteId}/auth/login", siteId)
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(jsonPath("$.error.code", is("NOT_FOUND")))
            .andExpect(jsonPath("$.error.message", is("Resource not found")))
            .andExpect(jsonPath("$.error.fields", empty()));

        verifyNoInteractions(currentUserService, registrationService, loginService);
    }
}
