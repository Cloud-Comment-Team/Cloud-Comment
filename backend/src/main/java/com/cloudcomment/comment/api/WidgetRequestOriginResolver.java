package com.cloudcomment.comment.api;

import com.cloudcomment.site.domain.SiteInputRules;
import jakarta.servlet.http.HttpServletRequest;
import com.cloudcomment.shared.web.security.WidgetRequestContext;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

@Component
class WidgetRequestOriginResolver {

    String resolve(HttpServletRequest request) {
        Optional<WidgetRequestContext> context = WidgetRequestContext.resolve(request);
        if (context.isPresent()) {
            return context.orElseThrow().origin();
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank()) {
            return origin;
        }
        return originFromReferer(request.getHeader(HttpHeaders.REFERER)).orElse(null);
    }

    private Optional<String> originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return Optional.empty();
        }

        URI uri;
        try {
            uri = URI.create(referer.trim());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if ((!"http".equals(scheme) && !"https".equals(scheme))
            || host == null
            || uri.getUserInfo() != null
            || uri.getPort() > 65535) {
            return Optional.empty();
        }

        String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
        return SiteInputRules.normalizeOrigin(scheme + "://" + host + port);
    }
}
