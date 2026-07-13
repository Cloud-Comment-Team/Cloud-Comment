package com.cloudcomment.shared.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCookieServicesTests {

    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void localSessionCookieIsHostOnlyHttpOnlyAndUsesConfiguredNonSecureName() {
        AdminSessionProperties properties = properties(
            "cloud_comment_admin_session",
            "cloud_comment_admin_csrf",
            false
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AdminSessionCookieService(properties, CLOCK)
            .write(response, "session-token", NOW.plusSeconds(3600));

        assertThat(response.getHeader("Set-Cookie"))
            .contains("cloud_comment_admin_session=session-token")
            .contains("Path=/")
            .contains("Max-Age=3600")
            .contains("HttpOnly")
            .contains("SameSite=Strict")
            .doesNotContain("Domain=")
            .doesNotContain("Secure");
    }

    @Test
    void productionSessionAndCsrfCookiesUseHostPrefixAndSecureAttributes() {
        AdminSessionProperties properties = properties(
            "__Host-cloud_comment_admin_session",
            "__Host-cloud_comment_admin_csrf",
            true
        );
        MockHttpServletResponse sessionResponse = new MockHttpServletResponse();
        MockHttpServletResponse csrfResponse = new MockHttpServletResponse();

        new AdminSessionCookieService(properties, CLOCK)
            .write(sessionResponse, "session-token", NOW.plusSeconds(7200));
        new AdminCsrfTokenService(properties).issue(csrfResponse);

        assertThat(sessionResponse.getHeader("Set-Cookie"))
            .contains("__Host-cloud_comment_admin_session=session-token")
            .contains("Path=/")
            .contains("Max-Age=7200")
            .contains("HttpOnly")
            .contains("Secure")
            .contains("SameSite=Strict")
            .doesNotContain("Domain=");
        assertThat(csrfResponse.getHeader("Set-Cookie"))
            .contains("__Host-cloud_comment_admin_csrf=")
            .contains("Path=/")
            .contains("HttpOnly")
            .contains("Secure")
            .contains("SameSite=Strict")
            .doesNotContain("Domain=");
    }

    @Test
    void expiredLoginNeverCreatesNegativeCookieMaxAge() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AdminSessionCookieService service = new AdminSessionCookieService(
            properties("cloud_comment_admin_session", "cloud_comment_admin_csrf", false),
            CLOCK
        );

        service.write(response, "session-token", NOW.minusSeconds(1));

        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    private AdminSessionProperties properties(String sessionName, String csrfName, boolean secure) {
        return new AdminSessionProperties(sessionName, csrfName, "X-CSRF-Token", secure);
    }
}
