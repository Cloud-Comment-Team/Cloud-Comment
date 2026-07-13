package com.cloudcomment.comment.api;

import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.comment.domain.PageUrlRules;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.shared.web.error.ApiErrorResponse;
import com.cloudcomment.shared.web.security.WidgetRequestContext;
import com.cloudcomment.widgetcontext.application.ResolvedWidgetContext;
import com.cloudcomment.widgetcontext.application.WidgetContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
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
        "^/api/public/sites/[0-9a-fA-F-]{36}/config/?$"
    );
    private static final Pattern PAGE_SCOPED_PATH = Pattern.compile(
        "^/api/public/sites/[0-9a-fA-F-]{36}/(?:pages/comments|comments(?:/.*)?)/?$"
    );
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> LEGACY_ALLOWED_REQUEST_METHODS = Set.of(
        "GET", "POST", "PUT", "PATCH", "DELETE"
    );
    private static final Set<String> LEGACY_ALLOWED_REQUEST_HEADERS = Set.of(
        "authorization", "content-type", "accept"
    );
    private static final Set<String> FRAME_ALLOWED_HEADERS = Set.of(
        "authorization",
        "content-type",
        "accept",
        WidgetContextService.CONTEXT_HEADER.toLowerCase(Locale.ROOT),
        "x-cloudcomment-page-url"
    );
    private static final String LEGACY_ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    private static final String LEGACY_ALLOWED_HEADERS = "Authorization, Content-Type, Accept";
    private static final String FRAME_ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    private static final String FRAME_ALLOWED_HEADERS_VALUE =
        "Authorization, Content-Type, Accept, X-CloudComment-Widget-Context, X-CloudComment-Page-Url";
    private static final String MAX_AGE_SECONDS = "3600";

    private final DomainPolicyService domainPolicyService;
    private final WidgetRequestOriginResolver requestOriginResolver;
    private final WidgetContextService widgetContextService;
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

        String path = pathWithoutContext(request);
        String transportOrigin = request.getHeader(HttpHeaders.ORIGIN);
        String requestContextToken = request.getHeader(WidgetContextService.CONTEXT_HEADER);
        if (isBootstrap(path)
            || isExchange(path)
            || isFramePage(path)
            || (requestContextToken != null && !requestContextToken.isBlank())
            || requiresContext(request, path)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        applyVaryHeaders(response);

        if (isFramePage(path)) {
            filterChain.doFilter(request, new FrameResponse(response));
            return;
        }

        if (CorsUtils.isPreFlightRequest(request)) {
            handlePreflight(request, response, siteId.orElseThrow(), path, transportOrigin);
            return;
        }

        if (isExchange(path)) {
            if (!widgetContextService.acceptsFrameOrigin(transportOrigin)) {
                writeBootstrapError(response, request);
                return;
            }
            applyFrameCorsHeaders(response, transportOrigin);
            filterChain.doFilter(request, response);
            return;
        }

        String contextToken = requestContextToken;
        if (contextToken != null && !contextToken.isBlank()) {
            boolean safeMethod = SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT));
            boolean acceptedTransport = transportOrigin != null
                ? widgetContextService.acceptsFrameOrigin(transportOrigin)
                : widgetContextService.acceptsContextTransport(
                    null,
                    request.getHeader(HttpHeaders.HOST),
                    safeMethod
                );
            if (!acceptedTransport) {
                writeContextError(response, request);
                return;
            }
            ResolvedWidgetContext resolved;
            String canonicalRequestPageUrl = null;
            try {
                resolved = widgetContextService.resolve(siteId.orElseThrow(), contextToken);
                if (isPageScoped(path)) {
                    String pageUrl = request.getHeader("X-CloudComment-Page-Url");
                    if (pageUrl == null || !widgetContextService.matchesPage(resolved, pageUrl)) {
                        writeContextError(response, request);
                        return;
                    }
                    canonicalRequestPageUrl = PageUrlRules.normalize(pageUrl).orElse(null);
                }
            } catch (ApplicationException exception) {
                writeContextError(response, request);
                return;
            }
            WidgetRequestContext.bind(request, new WidgetRequestContext(
                resolved.id(),
                resolved.siteId(),
                resolved.origin(),
                canonicalRequestPageUrl,
                resolved.pageUrlHash()
            ));
            if (transportOrigin != null) {
                applyFrameCorsHeaders(response, transportOrigin);
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (requiresContext(request, path)) {
            writeContextError(response, request);
            return;
        }

        String requestOrigin = requestOriginResolver.resolve(request);
        boolean allowed = domainPolicyService.isOriginAllowed(siteId.orElseThrow(), requestOrigin);
        if (!allowed && shouldRecordRejectedInstallation(request)) {
            domainPolicyService.recordRejectedInstallation(siteId.orElseThrow(), requestOrigin);
        }
        if (!allowed) {
            writeNotFound(response, request);
            return;
        }
        if (transportOrigin != null && !transportOrigin.isBlank()) {
            applyLegacyCorsHeaders(response, transportOrigin);
        }
        filterChain.doFilter(request, response);
        if (shouldRecordSuccessfulInstallation(request, response)) {
            domainPolicyService.recordSuccessfulInstallation(siteId.orElseThrow(), requestOrigin);
        }
    }

    private void handlePreflight(
        HttpServletRequest request,
        HttpServletResponse response,
        UUID siteId,
        String path,
        String origin
    ) throws IOException {
        Set<String> requestedHeaders = requestedHeaders(request);
        String requestedMethod = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        boolean framePreflight = isExchange(path)
            || requestedHeaders.contains(WidgetContextService.CONTEXT_HEADER.toLowerCase(Locale.ROOT));

        if (framePreflight) {
            boolean validHeaders = requestedHeaders.stream().allMatch(FRAME_ALLOWED_HEADERS::contains);
            boolean contextPresent = isExchange(path)
                || requestedHeaders.contains(WidgetContextService.CONTEXT_HEADER.toLowerCase(Locale.ROOT));
            if (!widgetContextService.acceptsFrameOrigin(origin)
                || !validHeaders
                || !contextPresent
                || !isAllowedFrameMethod(requestedMethod)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            applyFrameCorsHeaders(response, origin);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        if (requiresContextForPreflight(path, requestedMethod)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean validLegacyRequest = requestedMethod != null
            && LEGACY_ALLOWED_REQUEST_METHODS.contains(requestedMethod.trim().toUpperCase(Locale.ROOT))
            && requestedHeaders.stream().allMatch(LEGACY_ALLOWED_REQUEST_HEADERS::contains);
        if (!validLegacyRequest) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean allowed = domainPolicyService.isOriginAllowed(siteId, origin);
        if (!allowed
            && PUBLIC_SITE_CONFIG_PATH.matcher(path).matches()
            && "GET".equalsIgnoreCase(requestedMethod)) {
            domainPolicyService.recordRejectedInstallation(siteId, origin);
        }
        if (allowed) {
            applyLegacyCorsHeaders(response, origin);
        }
        response.setStatus(allowed ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_NOT_FOUND);
    }

    private boolean requiresContext(HttpServletRequest request, String path) {
        if (isBootstrap(path) || isExchange(path) || isFramePage(path)) {
            return false;
        }
        if (path.contains("/auth/") || path.contains("/account/") || path.contains("/privacy/")) {
            return true;
        }
        if (!SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && !authorization.isBlank();
    }

    private boolean requiresContextForPreflight(String path, String method) {
        if (isBootstrap(path)) {
            return false;
        }
        if (path.contains("/auth/") || path.contains("/account/") || path.contains("/privacy/")) {
            return true;
        }
        return method != null && !SAFE_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    private Set<String> requestedHeaders(HttpServletRequest request) {
        String value = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .map(header -> header.toLowerCase(Locale.ROOT))
            .filter(header -> !header.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isAllowedFrameMethod(String method) {
        return method != null && Set.of("GET", "POST", "PUT", "PATCH", "DELETE")
            .contains(method.toUpperCase(Locale.ROOT));
    }

    private Optional<UUID> resolveSiteId(HttpServletRequest request) {
        Matcher matcher = PUBLIC_SITE_PATH.matcher(pathWithoutContext(request));
        return matcher.matches() ? Optional.of(UUID.fromString(matcher.group(1))) : Optional.empty();
    }

    private boolean shouldRecordRejectedInstallation(HttpServletRequest request) {
        return isInstallationConfigPath(request) && "GET".equalsIgnoreCase(request.getMethod());
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

    private boolean isPageScoped(String path) {
        return PAGE_SCOPED_PATH.matcher(path).matches();
    }

    private boolean isBootstrap(String path) {
        return path.endsWith("/widget-context/bootstrap");
    }

    private boolean isExchange(String path) {
        return path.endsWith("/widget-context/exchange");
    }

    private boolean isFramePage(String path) {
        return path.endsWith("/widget-frame");
    }

    private String pathWithoutContext(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return requestUri.startsWith(contextPath) ? requestUri.substring(contextPath.length()) : requestUri;
    }

    private void applyLegacyCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, LEGACY_ALLOWED_METHODS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, LEGACY_ALLOWED_HEADERS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, MAX_AGE_SECONDS);
    }

    private void applyFrameCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, FRAME_ALLOWED_METHODS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, FRAME_ALLOWED_HEADERS_VALUE);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, MAX_AGE_SECONDS);
    }

    private void applyVaryHeaders(HttpServletResponse response) {
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
    }

    private void writeContextError(HttpServletResponse response, HttpServletRequest request) throws IOException {
        writeError(response, request, HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_WIDGET_CONTEXT, "Invalid widget context");
    }

    private void writeBootstrapError(HttpServletResponse response, HttpServletRequest request) throws IOException {
        writeError(
            response,
            request,
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.INVALID_WIDGET_BOOTSTRAP,
            "Invalid widget bootstrap"
        );
    }

    private void writeNotFound(HttpServletResponse response, HttpServletRequest request) throws IOException {
        writeError(response, request, HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Resource not found");
    }

    private void writeError(
        HttpServletResponse response,
        HttpServletRequest request,
        HttpStatus status,
        ApiErrorCode code,
        String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            ApiErrorResponse.of(code, message, status.value(), request.getRequestURI())
        );
    }

    private static final class FrameResponse extends HttpServletResponseWrapper {

        private static final String X_FRAME_OPTIONS = "X-Frame-Options";

        private FrameResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            if (!X_FRAME_OPTIONS.equalsIgnoreCase(name)) {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!X_FRAME_OPTIONS.equalsIgnoreCase(name)) {
                super.addHeader(name, value);
            }
        }
    }
}
