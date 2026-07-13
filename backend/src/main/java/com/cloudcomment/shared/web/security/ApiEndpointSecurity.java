package com.cloudcomment.shared.web.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ApiEndpointSecurity {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final List<HandlerMapping> handlerMappings;

    public ApiEndpointSecurity(List<HandlerMapping> handlerMappings) {
        this.handlerMappings = List.copyOf(handlerMappings);
    }

    public boolean requiresAuthentication(HttpServletRequest request) {
        if (!isApiRequest(request)) {
            return false;
        }
        if (isRealtimeHandshakeRequest(request)) {
            return false;
        }

        HandlerResolution resolution = resolveHandler(request);
        return switch (resolution.status()) {
            case FOUND -> requiresAuthentication(resolution.handler());
            case NOT_FOUND -> false;
            case FAILED -> !isPublicApiPath(request);
        };
    }

    public boolean usesPublicWidgetBearer(HttpServletRequest request) {
        return normalizedPath(request).startsWith("/api/public/sites/");
    }

    public boolean requiresCsrf(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return false;
        }

        String path = normalizedPath(request);
        if (path.startsWith("/api/public/sites/") || path.equals("/api/realtime/ws")) {
            return false;
        }
        if (path.equals("/api/account/deletion-confirmations")) {
            return false;
        }
        if (path.startsWith("/api/auth/")) {
            return !path.equals("/api/auth/csrf");
        }
        return requiresAuthentication(request);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = normalizedPath(request);
        return path.equals("/api") || path.startsWith("/api/");
    }

    private boolean isRealtimeHandshakeRequest(HttpServletRequest request) {
        return normalizedPath(request).equals("/api/realtime/ws");
    }

    private String normalizedPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return requestUri.startsWith(contextPath)
            ? requestUri.substring(contextPath.length())
            : requestUri;
    }

    private HandlerResolution resolveHandler(HttpServletRequest request) {
        boolean failed = false;
        for (HandlerMapping handlerMapping : handlerMappings) {
            try {
                HandlerExecutionChain chain = handlerMapping.getHandler(request);
                if (chain != null) {
                    return HandlerResolution.found(chain.getHandler());
                }
            } catch (Exception exception) {
                failed = true;
            }
        }
        return failed ? HandlerResolution.failed() : HandlerResolution.notFound();
    }

    private boolean requiresAuthentication(Object handler) {
        return !(handler instanceof HandlerMethod handlerMethod && isPublic(handlerMethod));
    }

    private boolean isPublicApiPath(HttpServletRequest request) {
        for (HandlerMapping handlerMapping : handlerMappings) {
            if (!(handlerMapping instanceof RequestMappingHandlerMapping requestMappingHandlerMapping)) {
                continue;
            }

            for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping
                : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
                if (pathMatches(mapping.getKey(), request) && isPublic(mapping.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean pathMatches(RequestMappingInfo requestMappingInfo, HttpServletRequest request) {
        RequestCondition<?> patternsCondition = requestMappingInfo.getActivePatternsCondition();
        return patternsCondition.getMatchingCondition(request) != null;
    }

    private boolean isPublic(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PublicApi.class)
            || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PublicApi.class);
    }

    private enum HandlerResolutionStatus {
        FOUND,
        NOT_FOUND,
        FAILED
    }

    private record HandlerResolution(HandlerResolutionStatus status, Object handler) {

        static HandlerResolution found(Object handler) {
            return new HandlerResolution(HandlerResolutionStatus.FOUND, handler);
        }

        static HandlerResolution notFound() {
            return new HandlerResolution(HandlerResolutionStatus.NOT_FOUND, null);
        }

        static HandlerResolution failed() {
            return new HandlerResolution(HandlerResolutionStatus.FAILED, null);
        }
    }
}
