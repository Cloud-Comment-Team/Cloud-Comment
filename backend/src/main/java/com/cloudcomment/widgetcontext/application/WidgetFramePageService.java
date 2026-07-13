package com.cloudcomment.widgetcontext.application;

import com.cloudcomment.comment.persistence.PublicCommentRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.application.EmbedCodeProperties;
import com.cloudcomment.site.domain.SiteInputRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.net.URI;

@Service
public class WidgetFramePageService {

    private final PublicCommentRepository repository;
    private final String widgetOrigin;

    public WidgetFramePageService(
        PublicCommentRepository repository,
        EmbedCodeProperties embedCodeProperties
    ) {
        this.repository = repository;
        this.widgetOrigin = originFromUrl(embedCodeProperties.widgetBaseUrl());
    }

    @Transactional(readOnly = true)
    public WidgetFramePage build(UUID siteId) {
        if (repository.findActiveSite(siteId).isEmpty()) {
            throw new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
        }
        List<String> allowedOrigins = repository.findAllowedOriginsForActiveSite(siteId);
        String frameAncestors = allowedOrigins.isEmpty() ? "'none'" : String.join(" ", allowedOrigins);
        String csp = "default-src 'none'; script-src 'self' " + widgetOrigin
            + "; connect-src 'self' " + widgetOrigin + "; "
            + "style-src 'self' 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; "
            + "object-src 'none'; frame-ancestors " + frameAncestors;
        String html = """
            <!doctype html>
            <html lang="ru">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>CloudComment</title>
              <style>html,body{margin:0;padding:0;background:transparent}</style>
            </head>
            <body>
              <div id="cloud-comment-widget-frame" data-site-id="%s"></div>
              <script src="/widget/cloud-comment-widget-frame.js" defer></script>
            </body>
            </html>
            """.formatted(siteId);
        return new WidgetFramePage(html, csp);
    }

    public record WidgetFramePage(String html, String contentSecurityPolicy) {
    }

    private String originFromUrl(String value) {
        try {
            URI uri = URI.create(value);
            String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
            return SiteInputRules.normalizeOrigin(uri.getScheme() + "://" + uri.getHost() + port)
                .orElseThrow();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Widget base URL must have a valid HTTP(S) origin", exception);
        }
    }
}
