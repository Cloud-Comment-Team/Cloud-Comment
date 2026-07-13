package com.cloudcomment.shared.web.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

@Component
public class AdminCsrfTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String SAME_SITE = "Strict";

    private final AdminSessionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminCsrfTokenService(AdminSessionProperties properties) {
        this.properties = properties;
    }

    public IssuedCsrfToken issue(HttpServletResponse response) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ResponseCookie cookie = ResponseCookie.from(properties.csrfCookieName(), token)
            .httpOnly(true)
            .secure(properties.secure())
            .sameSite(SAME_SITE)
            .path("/")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new IssuedCsrfToken(properties.csrfHeaderName(), token);
    }

    public boolean matches(HttpServletRequest request) {
        Optional<String> cookieToken = cookieToken(request);
        String headerToken = request.getHeader(properties.csrfHeaderName());
        if (cookieToken.isEmpty() || headerToken == null || headerToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
            cookieToken.orElseThrow().getBytes(java.nio.charset.StandardCharsets.UTF_8),
            headerToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private Optional<String> cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
            .filter(cookie -> properties.csrfCookieName().equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst();
    }

    public record IssuedCsrfToken(String headerName, String token) {
    }
}
