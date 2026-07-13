package com.cloudcomment.widgetcontext.application;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class WidgetClientIpResolver {

    static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    static final int MAX_HEADER_LENGTH = 1_024;
    static final int MAX_FORWARDED_HOPS = 16;
    private static final String UNKNOWN_ADDRESS = "unknown";

    private final List<IpNetwork> trustedProxies;

    public WidgetClientIpResolver(
        @Value("${cloud-comment.security.trusted-proxies:127.0.0.1/32,::1/128}") String trustedProxies
    ) {
        this.trustedProxies = parseTrustedProxies(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        Optional<InetAddress> remoteAddress = parseLiteralAddress(request.getRemoteAddr());
        String fallback = remoteAddress.map(InetAddress::getHostAddress).orElse(UNKNOWN_ADDRESS);
        if (remoteAddress.isEmpty() || !isTrusted(remoteAddress.orElseThrow())) {
            return fallback;
        }

        List<String> forwardedHeaders = Collections.list(request.getHeaders(FORWARDED_FOR_HEADER));
        if (forwardedHeaders.size() != 1) {
            return fallback;
        }
        String forwardedFor = forwardedHeaders.getFirst();
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return fallback;
        }
        if (forwardedFor.length() > MAX_HEADER_LENGTH) {
            return fallback;
        }

        String[] values = forwardedFor.split(",", -1);
        if (values.length == 0 || values.length > MAX_FORWARDED_HOPS) {
            return fallback;
        }

        List<InetAddress> chain = new ArrayList<>(values.length);
        for (String value : values) {
            Optional<InetAddress> parsed = parseLiteralAddress(value);
            if (parsed.isEmpty()) {
                return fallback;
            }
            chain.add(parsed.orElseThrow());
        }

        for (int index = chain.size() - 1; index >= 0; index--) {
            InetAddress candidate = chain.get(index);
            if (!isTrusted(candidate)) {
                return candidate.getHostAddress();
            }
        }
        return fallback;
    }

    private boolean isTrusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(network -> network.contains(address));
    }

    private List<IpNetwork> parseTrustedProxies(String configuredNetworks) {
        if (configuredNetworks == null || configuredNetworks.isBlank()) {
            return List.of();
        }
        List<IpNetwork> networks = new ArrayList<>();
        for (String configuredNetwork : configuredNetworks.split(",", -1)) {
            String value = configuredNetwork.trim();
            int separator = value.lastIndexOf('/');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new IllegalArgumentException("Trusted proxy must use CIDR notation: " + value);
            }
            InetAddress networkAddress = parseLiteralAddress(value.substring(0, separator))
                .orElseThrow(() -> new IllegalArgumentException("Trusted proxy is not a literal IP: " + value));
            int prefixLength;
            try {
                prefixLength = Integer.parseInt(value.substring(separator + 1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Trusted proxy prefix is invalid: " + value, exception);
            }
            int bitLength = networkAddress.getAddress().length * Byte.SIZE;
            if (prefixLength < 0 || prefixLength > bitLength) {
                throw new IllegalArgumentException("Trusted proxy prefix is outside address range: " + value);
            }
            networks.add(new IpNetwork(networkAddress.getAddress(), prefixLength));
        }
        return List.copyOf(networks);
    }

    private static Optional<InetAddress> parseLiteralAddress(String rawAddress) {
        if (rawAddress == null) {
            return Optional.empty();
        }
        String value = rawAddress.trim();
        if (value.length() < 2 || value.length() > 45 || value.indexOf('%') >= 0) {
            return Optional.empty();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            if (value.indexOf(':') >= 0) {
                if (!value.matches("[0-9A-Fa-f:.]+")) {
                    return Optional.empty();
                }
                return Optional.of(InetAddress.getByName(value));
            }
            String[] octets = value.split("\\.", -1);
            if (octets.length != 4) {
                return Optional.empty();
            }
            byte[] address = new byte[4];
            for (int index = 0; index < octets.length; index++) {
                String octet = octets[index];
                if (octet.isEmpty()
                    || octet.length() > 3
                    || !octet.chars().allMatch(character -> character >= '0' && character <= '9')) {
                    return Optional.empty();
                }
                int parsed = Integer.parseInt(octet);
                if (parsed > 255) {
                    return Optional.empty();
                }
                address[index] = (byte) parsed;
            }
            return Optional.of(InetAddress.getByAddress(address));
        } catch (UnknownHostException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private record IpNetwork(byte[] networkAddress, int prefixLength) {

        private IpNetwork {
            networkAddress = networkAddress.clone();
        }

        private boolean contains(InetAddress candidate) {
            byte[] candidateAddress = candidate.getAddress();
            if (candidateAddress.length != networkAddress.length) {
                return false;
            }
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < fullBytes; index++) {
                if (candidateAddress[index] != networkAddress[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (candidateAddress[fullBytes] & mask) == (networkAddress[fullBytes] & mask);
        }
    }
}
