package com.cloudcomment.comment.api;

import com.cloudcomment.comment.application.DomainPolicyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class PublicWidgetCorsFilter extends OncePerRequestFilter {

    private static final Pattern PUBLIC_SITE_PATH = Pattern.compile(
        "^/api/public/sites/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/.*)?$"
    );
    private static final String ALLOWED_METHODS = "GET, POST, OPTIONS";
    private static final String ALLOWED_HEADERS = "Authorization, Content-Type, Accept";
    private static final String MAX_AGE_SECONDS = "3600";

    private final DomainPolicyService domainPolicyService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<UUID> siteId = resolveSiteId(request);
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean allowed = siteId.isPresent() && domainPolicyService.isOriginAllowed(siteId.orElseThrow(), origin);
        if (allowed) {
            applyCorsHeaders(response, origin);
        }

        if (CorsUtils.isPreFlightRequest(request) && siteId.isPresent()) {
            response.setStatus(allowed ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Optional<UUID> resolveSiteId(HttpServletRequest request) {
        String path = pathWithoutContext(request);
        Matcher matcher = PUBLIC_SITE_PATH.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(matcher.group(1)));
    }

    private String pathWithoutContext(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return requestUri.startsWith(contextPath)
            ? requestUri.substring(contextPath.length())
            : requestUri;
    }

    private void applyCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ALLOWED_METHODS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_HEADERS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, MAX_AGE_SECONDS);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
    }
}
