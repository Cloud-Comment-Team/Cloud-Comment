package com.cloudcomment.widgetcontext.api;

import com.cloudcomment.shared.web.security.PublicApi;
import com.cloudcomment.widgetcontext.application.WidgetContextService;
import com.cloudcomment.widgetcontext.application.WidgetClientIpResolver;
import com.cloudcomment.widgetcontext.application.WidgetFramePageService;
import com.cloudcomment.widgetcontext.application.WidgetSecurityRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@PublicApi
@RestController
@RequestMapping("/api/public/sites/{siteId}")
public class WidgetContextController {

    private final WidgetContextService widgetContextService;
    private final WidgetFramePageService framePageService;
    private final WidgetSecurityRateLimiter rateLimiter;
    private final WidgetClientIpResolver clientIpResolver;

    public WidgetContextController(
        WidgetContextService widgetContextService,
        WidgetFramePageService framePageService,
        WidgetSecurityRateLimiter rateLimiter,
        WidgetClientIpResolver clientIpResolver
    ) {
        this.widgetContextService = widgetContextService;
        this.framePageService = framePageService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/widget-context/bootstrap")
    WidgetBootstrapResponse bootstrap(
        @PathVariable UUID siteId,
        HttpServletRequest servletRequest,
        @Valid @RequestBody WidgetBootstrapRequest request
    ) {
        String origin = servletRequest.getHeader(HttpHeaders.ORIGIN);
        rateLimiter.checkBootstrap(siteId, origin, clientIpResolver.resolve(servletRequest));
        return WidgetBootstrapResponse.from(widgetContextService.bootstrap(
            siteId,
            origin,
            request.pageUrl(),
            request.publicKey()
        ));
    }

    @PostMapping("/widget-context/exchange")
    WidgetExchangeResponse exchange(
        @PathVariable UUID siteId,
        HttpServletRequest servletRequest,
        @Valid @RequestBody WidgetExchangeRequest request
    ) {
        String origin = servletRequest.getHeader(HttpHeaders.ORIGIN);
        rateLimiter.checkExchange(siteId, origin, clientIpResolver.resolve(servletRequest));
        return WidgetExchangeResponse.from(widgetContextService.exchange(
            siteId,
            origin,
            request.ticket(),
            request.proof()
        ));
    }

    @GetMapping(value = "/widget-frame", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> frame(@PathVariable UUID siteId) {
        WidgetFramePageService.WidgetFramePage frame = framePageService.build(siteId);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy", frame.contentSecurityPolicy())
            .header("Referrer-Policy", "no-referrer")
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.TEXT_HTML)
            .body(frame.html());
    }
}
