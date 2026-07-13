package com.cloudcomment.support;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;

public final class AdminSecurityTestSupport {

    public static final String SESSION_COOKIE_NAME = "cloud_comment_admin_session";
    public static final String CSRF_COOKIE_NAME = "cloud_comment_admin_csrf";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";
    public static final String CSRF_TOKEN = "test-csrf-token";

    private AdminSecurityTestSupport() {
    }

    public static RequestPostProcessor adminSession(String token) {
        return request -> {
            appendCookies(request, new Cookie(SESSION_COOKIE_NAME, token));
            return request;
        };
    }

    public static RequestPostProcessor csrf() {
        return request -> {
            appendCookies(request, new Cookie(CSRF_COOKIE_NAME, CSRF_TOKEN));
            request.addHeader(CSRF_HEADER_NAME, CSRF_TOKEN);
            return request;
        };
    }

    public static RequestPostProcessor adminRequest(String token) {
        return request -> {
            appendCookies(
                request,
                new Cookie(SESSION_COOKIE_NAME, token),
                new Cookie(CSRF_COOKIE_NAME, CSRF_TOKEN)
            );
            request.addHeader(CSRF_HEADER_NAME, CSRF_TOKEN);
            return request;
        };
    }

    private static void appendCookies(org.springframework.mock.web.MockHttpServletRequest request, Cookie... cookies) {
        Cookie[] existing = request.getCookies();
        if (existing == null || existing.length == 0) {
            request.setCookies(cookies);
            return;
        }
        Cookie[] combined = Arrays.copyOf(existing, existing.length + cookies.length);
        System.arraycopy(cookies, 0, combined, existing.length, cookies.length);
        request.setCookies(combined);
    }
}
