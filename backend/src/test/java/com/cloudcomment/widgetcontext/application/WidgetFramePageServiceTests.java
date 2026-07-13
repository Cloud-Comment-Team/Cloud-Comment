package com.cloudcomment.widgetcontext.application;

import com.cloudcomment.comment.application.WidgetSite;
import com.cloudcomment.comment.persistence.PublicCommentRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.application.EmbedCodeProperties;
import com.cloudcomment.site.domain.ModerationMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WidgetFramePageServiceTests {

    private static final String WIDGET_ORIGIN = "https://widget.example.net";

    @Test
    void buildsExternalScriptOnlyShellAndExactAllowlistCsp() {
        UUID siteId = UUID.randomUUID();
        PublicCommentRepository repository = mock(PublicCommentRepository.class);
        when(repository.findActiveSite(siteId)).thenReturn(Optional.of(
            new WidgetSite(siteId, ModerationMode.POST_MODERATION)
        ));
        when(repository.findAllowedOriginsForActiveSite(siteId)).thenReturn(List.of(
            "https://card.example.com",
            "https://docs.example.com:8443"
        ));
        WidgetFramePageService service = service(repository);

        WidgetFramePageService.WidgetFramePage page = service.build(siteId);

        assertThat(page.html())
            .contains("data-site-id=\"" + siteId + "\"")
            .contains("<script src=\"/widget/cloud-comment-widget-frame.js\" defer></script>")
            .contains("<style>html,body{margin:0;padding:0;background:transparent}</style>")
            .doesNotContain("<script>")
            .doesNotContain("http-equiv");
        assertThat(page.contentSecurityPolicy()).isEqualTo(
            "default-src 'none'; script-src 'self' https://widget.example.net; "
                + "connect-src 'self' https://widget.example.net; style-src 'self' 'unsafe-inline'; img-src data:; "
                + "base-uri 'none'; form-action 'none'; object-src 'none'; "
                + "frame-ancestors https://card.example.com https://docs.example.com:8443"
        );
    }

    @Test
    void usesNoneWhenActiveSiteHasNoAllowedOrigins() {
        UUID siteId = UUID.randomUUID();
        PublicCommentRepository repository = mock(PublicCommentRepository.class);
        when(repository.findActiveSite(siteId)).thenReturn(Optional.of(
            new WidgetSite(siteId, ModerationMode.PRE_MODERATION)
        ));
        when(repository.findAllowedOriginsForActiveSite(siteId)).thenReturn(List.of());

        assertThat(service(repository).build(siteId).contentSecurityPolicy())
            .endsWith("frame-ancestors 'none'");
    }

    @Test
    void masksMissingOrInactiveSite() {
        UUID siteId = UUID.randomUUID();
        PublicCommentRepository repository = mock(PublicCommentRepository.class);
        when(repository.findActiveSite(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(repository).build(siteId))
            .isInstanceOfSatisfying(ApplicationException.class, exception ->
                assertThat(exception.code()).isEqualTo(ApiErrorCode.NOT_FOUND)
            );
    }

    private WidgetFramePageService service(PublicCommentRepository repository) {
        return new WidgetFramePageService(
            repository,
            new EmbedCodeProperties(
                WIDGET_ORIGIN + "/widget/cloud-comment-widget.js",
                "https://api.example.net/api",
                WIDGET_ORIGIN
            )
        );
    }
}
