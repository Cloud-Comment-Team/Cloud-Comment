package com.cloudcomment.site.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SiteInputRulesTests {

    @Test
    void normalizeDomainTrimsAndLowercasesHostname() {
        assertThat(SiteInputRules.normalizeDomain(" Example.COM "))
            .contains("example.com");
    }

    @Test
    void normalizeDomainRejectsSchemePathAndInvalidHostname() {
        assertThat(SiteInputRules.normalizeDomain("https://example.com")).isEmpty();
        assertThat(SiteInputRules.normalizeDomain("example.com/path")).isEmpty();
        assertThat(SiteInputRules.normalizeDomain("-example.com")).isEmpty();
    }

    @Test
    void normalizeOriginsAllowsHttpHttpsOriginsAndDeduplicates() {
        assertThat(SiteInputRules.normalizeOrigins(List.of(
            "HTTPS://Example.COM:8443",
            "https://example.com:8443",
            "http://localhost:3000"
        ))).containsExactly("https://example.com:8443", "http://localhost:3000");
    }

    @Test
    void normalizeOriginsRejectsPathQueryFragmentAndUnsupportedScheme() {
        assertThat(SiteInputRules.normalizeOrigins(List.of("https://example.com/path"))).isEmpty();
        assertThat(SiteInputRules.normalizeOrigins(List.of("https://example.com?x=1"))).isEmpty();
        assertThat(SiteInputRules.normalizeOrigins(List.of("https://example.com:99999"))).isEmpty();
        assertThat(SiteInputRules.normalizeOrigins(List.of("ftp://example.com"))).isEmpty();
    }
}
