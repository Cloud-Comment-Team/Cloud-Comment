package com.cloudcomment.site.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmbedCodeServiceTests {

    @Test
    void buildReturnsConfiguredUrlsAndEscapesHtmlAttributes() {
        EmbedCodeService service = new EmbedCodeService(new EmbedCodeProperties(
            "https://cdn.example.com/widget.js?tenant=<cloud>",
            "https://api.example.com/api?source=<admin>",
            "https://frame.example.com/Cloud-Comment"
        ));
        UUID siteId = UUID.randomUUID();

        EmbedCode embedCode = service.build(siteId);

        assertThat(embedCode.siteId()).isEqualTo(siteId);
        assertThat(embedCode.scriptUrl()).isEqualTo("https://cdn.example.com/widget.js?tenant=<cloud>");
        assertThat(embedCode.dataAttributes())
            .containsEntry("siteId", siteId.toString())
            .containsEntry("apiBaseUrl", "https://api.example.com/api?source=<admin>")
            .containsEntry("frameBaseUrl", "https://frame.example.com/Cloud-Comment");
        assertThat(embedCode.embedCode())
            .contains("tenant=&lt;cloud&gt;")
            .contains("data-site-id=\"" + siteId + "\"")
            .contains("source=&lt;admin&gt;")
            .contains("data-frame-base-url=\"https://frame.example.com/Cloud-Comment\"");
    }
}
