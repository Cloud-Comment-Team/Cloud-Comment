package com.cloudcomment.shared.web.security;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

@Component
public class AdminSessionCookieService {

    private static final String SAME_SITE = "Strict";
    private static final String PATH = "/";

    private final AdminSessionProperties properties;
    private final Clock clock;

    public AdminSessionCookieService(AdminSessionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String resolve(HttpServletRequest request) {
        return resolveOptional(request).orElseThrow(this::invalidSession);
    }

    public Optional<String> resolveOptional(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> properties.cookieName().equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst();
    }

    public void write(HttpServletResponse response, String token, Instant expiresAt) {
        Duration remainingTtl = Duration.between(clock.instant(), expiresAt);
        if (remainingTtl.isNegative()) {
            remainingTtl = Duration.ZERO;
        }
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            sessionCookie(token).mutate().maxAge(remainingTtl).build().toString()
        );
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            sessionCookie("").mutate().maxAge(Duration.ZERO).build().toString()
        );
    }

    private ResponseCookie sessionCookie(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
            .httpOnly(true)
            .secure(properties.secure())
            .sameSite(SAME_SITE)
            .path(PATH)
            .build();
    }

    private ApplicationException invalidSession() {
        return new ApplicationException(
            ApiErrorCode.INVALID_SESSION,
            "Invalid or expired session"
        );
    }
}
