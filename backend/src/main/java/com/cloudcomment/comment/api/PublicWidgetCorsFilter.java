package com.cloudcomment.comment.api;

import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.web.error.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class PublicWidgetCorsFilter extends OncePerRequestFilter {

    private static final Pattern PUBLIC_SITE_PATH = Pattern.compile(
        "^/api/public/sites/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/.*)?$"
    );
    private static final Pattern PUBLIC_SITE_CONFIG_PATH = Pattern.compile(
        "^/api/public/sites/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/config/?$"
    );
    private static final Set<String> ALLOWED_REQUEST_METHODS = Set.of(
        "GET", "POST", "PUT", "PATCH", "DELETE"
    );
    private static final Set<String> ALLOWED_REQUEST_HEADERS = Set.of(
        "authorization", "content-type", "accept"
    );
    private static final String ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    private static final String ALLOWED_HEADERS = "Authorization, Content-Type, Accept";
    private static final String MAX_AGE_SECONDS = "3600";

    private final DomainPolicyService domainPolicyService;
    private final WidgetRequestOriginResolver requestOriginResolver;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<UUID> siteId = resolveSiteId(request);
        if (siteId.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String corsOrigin = request.getHeader(HttpHeaders.ORIGIN);
        String requestOrigin = requestOriginResolver.resolve(request);
        boolean allowed = domainPolicyService.isOriginAllowed(siteId.orElseThrow(), requestOrigin);
        if (!allowed && shouldRecordRejectedInstallation(request)) {
            domainPolicyService.recordRejectedInstallation(siteId.orElseThrow(), requestOrigin);
        }
        applyVaryHeaders(response);

        if (CorsUtils.isPreFlightRequest(request)) {
            boolean validPreflight = allowed && isAllowedPreflight(request);
            if (validPreflight) {
                applyCorsHeaders(response, corsOrigin);
            }
            response.setStatus(
                validPreflight ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_NOT_FOUND
            );
            return;
        }

        if (!allowed) {
            writeNotFound(response, request);
            return;
        }
        if (corsOrigin != null && !corsOrigin.isBlank()) {
            applyCorsHeaders(response, corsOrigin);
        }

        filterChain.doFilter(request, response);
        if (shouldRecordSuccessfulInstallation(request, response)) {
            domainPolicyService.recordSuccessfulInstallation(siteId.orElseThrow(), requestOrigin);
        }
    }

    private Optional<UUID> resolveSiteId(HttpServletRequest request) {
        String path = pathWithoutContext(request);
        Matcher matcher = PUBLIC_SITE_PATH.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(matcher.group(1)));
    }

    private boolean shouldRecordRejectedInstallation(HttpServletRequest request) {
        if (!isInstallationConfigPath(request)) {
            return false;
        }
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return CorsUtils.isPreFlightRequest(request)
            && "GET".equalsIgnoreCase(request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD));
    }

    private boolean shouldRecordSuccessfulInstallation(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        return isInstallationConfigPath(request)
            && "GET".equalsIgnoreCase(request.getMethod())
            && response.getStatus() >= 200
            && response.getStatus() < 300;
    }

    private boolean isInstallationConfigPath(HttpServletRequest request) {
        return PUBLIC_SITE_CONFIG_PATH.matcher(pathWithoutContext(request)).matches();
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
    }

    private boolean isAllowedPreflight(HttpServletRequest request) {
        String method = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        if (method == null
            || !ALLOWED_REQUEST_METHODS.contains(method.trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        return requestedHeaders(request).stream().allMatch(ALLOWED_REQUEST_HEADERS::contains);
    }

    private Set<String> requestedHeaders(HttpServletRequest request) {
        String value = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(header -> !header.isBlank())
            .map(header -> header.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    private void applyVaryHeaders(HttpServletResponse response) {
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
    }

    private void writeNotFound(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            ApiErrorResponse.of(
                ApiErrorCode.NOT_FOUND,
                "Resource not found",
                HttpStatus.NOT_FOUND.value(),
                request.getRequestURI()
            )
        );
    }
}
