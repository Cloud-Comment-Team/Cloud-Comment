package com.cloudcomment.site.domain;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class SiteInputRules {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^(localhost|(?=.{1,255}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63})$"
    );
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    );
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    private SiteInputRules() {
    }

    public static Optional<String> normalizeDomain(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()
            || normalized.length() > 255
            || normalized.contains("://")
            || normalized.contains("/")
            || normalized.contains("?")
            || normalized.contains("#")
            || WHITESPACE_PATTERN.matcher(normalized).find()
            || !DOMAIN_PATTERN.matcher(normalized).matches()) {
            return Optional.empty();
        }

        return Optional.of(normalized);
    }

    public static Optional<String> normalizeOrigin(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        if (trimmed.isBlank() || trimmed.length() > 255 || WHITESPACE_PATTERN.matcher(trimmed).find()) {
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
        String path = uri.getRawPath();
        if ((!"http".equals(scheme) && !"https".equals(scheme))
            || host == null
            || !isValidOriginHost(host)
            || uri.getUserInfo() != null
            || uri.getPort() > 65535
            || (path != null && !path.isEmpty())
            || uri.getRawQuery() != null
            || uri.getRawFragment() != null) {
            return Optional.empty();
        }

        String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
        return Optional.of(scheme + "://" + host + port);
    }

    public static List<String> normalizeOrigins(List<String> origins) {
        if (origins == null) {
            return List.of();
        }

        Set<String> normalizedOrigins = new LinkedHashSet<>();
        for (String origin : origins) {
            Optional<String> normalizedOrigin = normalizeOrigin(origin);
            if (normalizedOrigin.isEmpty()) {
                return List.of();
            }
            normalizedOrigins.add(normalizedOrigin.orElseThrow());
        }
        return List.copyOf(normalizedOrigins);
    }

    private static boolean isValidOriginHost(String host) {
        return DOMAIN_PATTERN.matcher(host).matches() || IPV4_PATTERN.matcher(host).matches();
    }
}
