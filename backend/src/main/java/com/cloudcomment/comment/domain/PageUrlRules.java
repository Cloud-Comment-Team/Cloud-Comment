package com.cloudcomment.comment.domain;

import com.cloudcomment.site.domain.SiteInputRules;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class PageUrlRules {

    private static final int MAX_PAGE_URL_LENGTH = 2048;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    private PageUrlRules() {
    }

    public static Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        if (trimmed.isBlank()
            || trimmed.length() > MAX_PAGE_URL_LENGTH
            || WHITESPACE_PATTERN.matcher(trimmed).find()) {
            return Optional.empty();
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;
        if ((!"http".equals(scheme) && !"https".equals(scheme))
            || host == null
            || uri.getUserInfo() != null
            || uri.getRawFragment() != null
            || uri.getPort() > 65535) {
            return Optional.empty();
        }

        String origin = buildOrigin(scheme, host, uri.getPort());
        if (SiteInputRules.normalizeOrigin(origin).isEmpty()) {
            return Optional.empty();
        }

        String path = uri.getRawPath();
        String normalizedPath = path == null || path.isEmpty() ? "/" : path;
        String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
        return Optional.of(origin + normalizedPath + query);
    }

    public static Optional<String> originOf(String normalizedPageUrl) {
        return normalize(normalizedPageUrl).flatMap(pageUrl -> {
            URI uri = URI.create(pageUrl);
            return Optional.of(buildOrigin(uri.getScheme(), uri.getHost(), uri.getPort()));
        });
    }

    private static String buildOrigin(String scheme, String host, int port) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String portPart = port >= 0 ? ":" + port : "";
        return scheme.toLowerCase(Locale.ROOT) + "://" + normalizedHost + portPart;
    }
}
