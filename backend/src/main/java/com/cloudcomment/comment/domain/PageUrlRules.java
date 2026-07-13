package com.cloudcomment.comment.domain;

import com.cloudcomment.site.domain.SiteInputRules;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class PageUrlRules {

    private static final int MAX_PAGE_URL_LENGTH = 2048;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");
    private static final Set<String> TRACKING_PARAMETER_NAMES = Set.of(
        "fbclid", "gclid", "dclid", "msclkid", "yclid", "twclid", "ttclid", "igshid", "li_fat_id",
        "_ga", "_gl", "mc_cid", "mc_eid", "gbraid", "wbraid", "srsltid", "gad_source", "gad_campaignid"
    );
    private static final Set<String> SENSITIVE_PARAMETER_NAMES = Set.of(
        "access_token", "id_token", "refresh_token", "auth_token", "authorization", "api_key", "apikey",
        "jwt", "password", "session", "session_id", "sessionid", "token", "client_secret", "secret",
        "signature", "sig", "otp", "jsessionid", "phpsessid"
    );
    private static final Set<String> SENSITIVE_PARAMETER_PREFIXES = Set.of("x-amz-", "x-goog-");

    private PageUrlRules() {
    }

    public static Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        int fragmentStart = trimmed.indexOf('#');
        String withoutFragment = fragmentStart >= 0 ? trimmed.substring(0, fragmentStart) : trimmed;
        String percentSafeUrl = escapeInvalidPercents(withoutFragment);
        if (withoutFragment.isBlank()
            || trimmed.length() > MAX_PAGE_URL_LENGTH
            || percentSafeUrl.length() > MAX_PAGE_URL_LENGTH
            || WHITESPACE_PATTERN.matcher(withoutFragment).find()) {
            return Optional.empty();
        }

        URI uri;
        try {
            uri = URI.create(percentSafeUrl);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;
        if ((!"http".equals(scheme) && !"https".equals(scheme))
            || host == null
            || uri.getUserInfo() != null
            || uri.getPort() > 65535) {
            return Optional.empty();
        }

        Optional<String> normalizedOrigin = SiteInputRules.normalizeOrigin(buildOrigin(scheme, host, uri.getPort()));
        if (normalizedOrigin.isEmpty()) {
            return Optional.empty();
        }
        String origin = normalizedOrigin.orElseThrow();

        String path = uri.getRawPath();
        String normalizedPath = path == null || path.isEmpty() ? "/" : path;
        String canonicalQuery = canonicalizeQuery(uri.getRawQuery());
        String query = canonicalQuery.isEmpty() ? "" : "?" + canonicalQuery;
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
        boolean defaultPort = ("https".equalsIgnoreCase(scheme) && port == 443)
            || ("http".equalsIgnoreCase(scheme) && port == 80);
        String portPart = port >= 0 && !defaultPort ? ":" + port : "";
        return scheme.toLowerCase(Locale.ROOT) + "://" + normalizedHost + portPart;
    }

    private static String canonicalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }

        return Arrays.stream(rawQuery.split("&", -1))
            .filter(parameter -> parameter.isEmpty() || !isPrivateParameter(parameter))
            .collect(Collectors.joining("&"));
    }

    private static boolean isPrivateParameter(String rawParameter) {
        int separator = rawParameter.indexOf('=');
        String rawName = separator >= 0 ? rawParameter.substring(0, separator) : rawParameter;
        String name = decodePercentEncodedName(rawName).toLowerCase(Locale.ROOT);
        return name.startsWith("utm_")
            || TRACKING_PARAMETER_NAMES.contains(name)
            || SENSITIVE_PARAMETER_NAMES.contains(name)
            || SENSITIVE_PARAMETER_PREFIXES.stream().anyMatch(name::startsWith)
            || name.endsWith("_token");
    }

    private static String escapeInvalidPercents(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            boolean validEscape = current == '%'
                && index + 2 < value.length()
                && Character.digit(value.charAt(index + 1), 16) >= 0
                && Character.digit(value.charAt(index + 2), 16) >= 0;
            escaped.append(current == '%' && !validEscape ? "%25" : current);
        }
        return escaped.toString();
    }

    private static String decodePercentEncodedName(String rawName) {
        StringBuilder decoded = new StringBuilder(rawName.length());
        int index = 0;
        while (index < rawName.length()) {
            if (rawName.charAt(index) != '%' || index + 2 >= rawName.length()) {
                decoded.append(rawName.charAt(index));
                index++;
                continue;
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (index + 2 < rawName.length() && rawName.charAt(index) == '%') {
                int high = Character.digit(rawName.charAt(index + 1), 16);
                int low = Character.digit(rawName.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    break;
                }
                bytes.write((high << 4) + low);
                index += 3;
            }
            if (bytes.size() == 0) {
                decoded.append(rawName.charAt(index));
                index++;
            } else {
                decoded.append(bytes.toString(StandardCharsets.UTF_8));
            }
        }
        return decoded.toString();
    }
}
