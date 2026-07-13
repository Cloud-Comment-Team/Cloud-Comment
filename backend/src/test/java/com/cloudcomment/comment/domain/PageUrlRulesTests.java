package com.cloudcomment.comment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageUrlRulesTests {

    @Test
    void normalizeLowercasesOriginAndPreservesPathAndQuery() {
        assertThat(PageUrlRules.normalize(" HTTPS://Example.COM:8443/blog/Post-1?x=1&utm_source=mail#comments "))
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
        assertThat(PageUrlRules.normalize("https://example.com/white space")).isEmpty();
    }

    @Test
    void normalizeStripsFragmentAndEmptyQuery() {
        assertThat(PageUrlRules.normalize("https://example.com/page?#fragment with spaces"))
            .contains("https://example.com/page");
    }

    @Test
    void normalizeRemovesTrackingAndSensitiveNamesCaseInsensitivelyAfterPercentDecoding() {
        assertThat(PageUrlRules.normalize(
            "https://example.com/page?%75tm_source=mail&FBCLID=click&api%5Fkey=secret&csrf%5Ftoken=secret&tab=comments"
        )).contains("https://example.com/page?tab=comments");
    }

    @Test
    void normalizePreservesRawEncodingOrderAndRepeatedFunctionalParameters() {
        assertThat(PageUrlRules.normalize(
            "https://example.com/search?q=hello+world&&q=hello%20world&next=%2Ffeed&anchor=%23item&utm_medium=email&tab=all&"
        )).contains("https://example.com/search?q=hello+world&&q=hello%20world&next=%2Ffeed&anchor=%23item&tab=all&");
    }

    @Test
    void normalizeAppliesLengthLimitToOriginalUrlIncludingFragment() {
        assertThat(PageUrlRules.normalize("https://example.com/#" + "x".repeat(2048))).isEmpty();
    }

    @Test
    void normalizeKeepsUnknownNamesAndRemovesOnlySensitiveSuffix() {
        assertThat(PageUrlRules.normalize(
            "https://example.com/page?campaign=summer&tokenize=true&custom_token=secret&mc_eid=mail"
                + "&gbraid=a&wbraid=b&srsltid=c&gad_source=d&gad_campaignid=e&client_secret=secret"
                + "&secret=value&signature=s&sig=s&otp=1&jsessionid=3&phpsessid=4"
                + "&x-amz-signature=aws&x-goog-credential=goog&x-amazing=keep&filter=recent"
        )).contains("https://example.com/page?campaign=summer&tokenize=true&x-amazing=keep&filter=recent");
    }

    @Test
    void normalizePreservesAmbiguousFunctionalCodeTicketAndSidParameters() {
        assertThat(PageUrlRules.normalize("https://example.com/callback?code=flow-a&ticket=thread-a&sid=section-a"))
            .contains("https://example.com/callback?code=flow-a&ticket=thread-a&sid=section-a");
        assertThat(PageUrlRules.normalize("https://example.com/callback?code=flow-b&ticket=thread-b&sid=section-b"))
            .contains("https://example.com/callback?code=flow-b&ticket=thread-b&sid=section-b");
    }

    @Test
    void normalizeEscapesMalformedPercentAndPreservesValidEscapes() {
        assertThat(PageUrlRules.normalize("https://example.com/search?q=100%"))
            .contains("https://example.com/search?q=100%25");
        assertThat(PageUrlRules.normalize("https://example.com/search?%ZZ=x"))
            .contains("https://example.com/search?%25ZZ=x");
        assertThat(PageUrlRules.normalize("https://example.com/search?next=%2Ffeed"))
            .contains("https://example.com/search?next=%2Ffeed");
    }

    @Test
    void normalizeRemovesDefaultPortsFromOriginAndPageIdentity() {
        assertThat(PageUrlRules.normalize("https://Example.com:443/page?tab=comments"))
            .contains("https://example.com/page?tab=comments");
        assertThat(PageUrlRules.normalize("http://Example.com:80/page"))
            .contains("http://example.com/page");
        assertThat(PageUrlRules.normalize("https://Example.com:8443/page"))
            .contains("https://example.com:8443/page");
        assertThat(PageUrlRules.originOf("https://Example.com:443/page"))
            .contains("https://example.com");
    }

    @Test
    void originOfReturnsNormalizedOrigin() {
        assertThat(PageUrlRules.originOf("https://Example.com:8443/path?q=1"))
            .contains("https://example.com:8443");
    }
}
