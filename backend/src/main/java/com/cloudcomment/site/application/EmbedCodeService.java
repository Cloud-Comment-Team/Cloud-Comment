package com.cloudcomment.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class EmbedCodeService {

    private final EmbedCodeProperties properties;

    EmbedCode build(UUID siteId) {
        String siteIdValue = siteId.toString();
        String embedCode = """
            <script src="%s" data-site-id="%s" data-api-base-url="%s" data-frame-base-url="%s"></script>\
            """.formatted(
            HtmlUtils.htmlEscape(properties.scriptUrl()),
            HtmlUtils.htmlEscape(siteIdValue),
            HtmlUtils.htmlEscape(properties.apiBaseUrl()),
            HtmlUtils.htmlEscape(properties.widgetBaseUrl())
        );

        return new EmbedCode(
            siteId,
            properties.scriptUrl(),
            embedCode,
            Map.of(
                "siteId", siteIdValue,
                "apiBaseUrl", properties.apiBaseUrl(),
                "frameBaseUrl", properties.widgetBaseUrl()
            )
        );
    }
}
