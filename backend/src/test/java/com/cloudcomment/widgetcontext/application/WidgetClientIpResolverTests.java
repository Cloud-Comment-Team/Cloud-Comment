package com.cloudcomment.widgetcontext.application;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WidgetClientIpResolverTests {

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        WidgetClientIpResolver resolver = new WidgetClientIpResolver("127.0.0.1/32,::1/128");
        MockHttpServletRequest request = request("198.51.100.25", "203.0.113.40");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.25");
    }

    @Test
    void resolvesFirstUntrustedAddressFromRightThroughTrustedProxyChain() {
        WidgetClientIpResolver resolver = new WidgetClientIpResolver("10.0.0.0/8,127.0.0.1/32,::1/128");
        MockHttpServletRequest request = request(
            "10.30.0.5",
            "198.51.100.111, 203.0.113.40, 10.20.0.7"
        );

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.40");
    }

    @Test
    void acceptsLiteralIpv6AndConfiguredIpv6ProxyNetwork() {
        WidgetClientIpResolver resolver = new WidgetClientIpResolver("2001:db8:1::/48");
        MockHttpServletRequest request = request("2001:db8:1::10", "2001:db8:2::20");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8:2:0:0:0:0:20");
    }

    @Test
    void fallsBackToImmediatePeerForMalformedOrOversizedForwardingChain() {
        WidgetClientIpResolver resolver = new WidgetClientIpResolver("127.0.0.1/32");

        assertThat(resolver.resolve(request("127.0.0.1", "203.0.113.10, attacker.example")))
            .isEqualTo("127.0.0.1");
        MockHttpServletRequest duplicateHeaders = request("127.0.0.1", "203.0.113.10");
        duplicateHeaders.addHeader(WidgetClientIpResolver.FORWARDED_FOR_HEADER, "198.51.100.10");
        assertThat(resolver.resolve(duplicateHeaders)).isEqualTo("127.0.0.1");
        String tooManyHops = IntStream.range(0, WidgetClientIpResolver.MAX_FORWARDED_HOPS + 1)
            .mapToObj(index -> "192.0.2." + index)
            .collect(Collectors.joining(","));
        assertThat(resolver.resolve(request("127.0.0.1", tooManyHops))).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve(request(
            "127.0.0.1",
            "1".repeat(WidgetClientIpResolver.MAX_HEADER_LENGTH + 1)
        ))).isEqualTo("127.0.0.1");
    }

    @Test
    void rejectsInvalidTrustedProxyConfigurationAtStartup() {
        assertThatThrownBy(() -> new WidgetClientIpResolver("proxy.internal/24"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("literal IP");
        assertThatThrownBy(() -> new WidgetClientIpResolver("127.0.0.1/64"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outside address range");
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader(WidgetClientIpResolver.FORWARDED_FOR_HEADER, forwardedFor);
        return request;
    }
}
