package com.cloudcomment.shared.web.security;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.UUID;

public record WidgetRequestContext(
    UUID contextId,
    UUID siteId,
    String origin,
    String canonicalPageUrl,
    String pageUrlHash
) {

    private static final String ATTRIBUTE = WidgetRequestContext.class.getName();

    public static void bind(HttpServletRequest request, WidgetRequestContext context) {
        request.setAttribute(ATTRIBUTE, context);
    }

    public static Optional<WidgetRequestContext> resolve(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof WidgetRequestContext context ? Optional.of(context) : Optional.empty();
    }

    public static WidgetRequestContext require(HttpServletRequest request) {
        return resolve(request).orElseThrow(WidgetRequestContext::invalidContext);
    }

    private static ApplicationException invalidContext() {
        return new ApplicationException(ApiErrorCode.INVALID_WIDGET_CONTEXT, "Invalid widget context");
    }
}
