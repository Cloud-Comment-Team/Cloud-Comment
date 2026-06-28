package com.cloudcomment.comment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageUrlRulesTests {

    @Test
    void normalizeLowercasesOriginAndPreservesPathAndQuery() {
        assertThat(PageUrlRules.normalize(" HTTPS://Example.COM:8443/blog/Post-1?x=1 "))
            .contains("https://example.com:8443/blog/Post-1?x=1");
    }

    @Test
    void normalizeAddsRootPathWhenPathIsEmpty() {
        assertThat(PageUrlRules.normalize("https://example.com"))
            .contains("https://example.com/");
    }

    @Test
    void normalizeRejectsUnsupportedOrUnsafeUrls() {
        assertThat(PageUrlRules.normalize("ftp://example.com/page")).isEmpty();
        assertThat(PageUrlRules.normalize("https://user@example.com/page")).isEmpty();
        assertThat(PageUrlRules.normalize("https://example.com/page#fragment")).isEmpty();
        assertThat(PageUrlRules.normalize("https://example.com/white space")).isEmpty();
    }

    @Test
    void originOfReturnsNormalizedOrigin() {
        assertThat(PageUrlRules.originOf("https://Example.com:8443/path?q=1"))
            .contains("https://example.com:8443");
    }
}
