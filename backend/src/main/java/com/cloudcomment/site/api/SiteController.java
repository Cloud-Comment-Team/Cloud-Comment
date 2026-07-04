package com.cloudcomment.site.api;

import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.web.PaginatedResponse;
import com.cloudcomment.shared.web.security.CurrentUser;
import com.cloudcomment.site.application.SitePage;
import com.cloudcomment.site.application.SiteService;
import com.cloudcomment.site.domain.Site;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
class SiteController {

    private final SiteService siteService;

    @GetMapping
    PaginatedResponse<SiteResponse> listSites(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(defaultValue = "1") @Min(1) @Max(100_000) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        SitePage sites = siteService.listSites(currentUser, page, pageSize);
        return PaginatedResponse.of(
            sites.items().stream().map(SiteResponse::from).toList(),
            sites.page(),
            sites.pageSize(),
            sites.totalItems()
        );
    }

    @PostMapping
    ResponseEntity<SiteResponse> createSite(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody CreateSiteRequest request
    ) {
        Site site = siteService.createSite(
            currentUser,
            request.name(),
            request.domain(),
            request.moderationMode(),
            request.allowedOrigins(),
            request.widgetStyle() != null ? request.widgetStyle().toDomainOrDefault() : null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(SiteResponse.from(site));
    }

    @GetMapping("/{siteId}")
    SiteResponse getSite(@CurrentUser AuthenticatedUser currentUser, @PathVariable UUID siteId) {
        return SiteResponse.from(siteService.getSite(currentUser, siteId));
    }

    @PatchMapping("/{siteId}")
    SiteResponse updateSite(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @Valid @RequestBody UpdateSiteRequest request
    ) {
        return SiteResponse.from(siteService.updateSite(
            currentUser,
            siteId,
            request.name(),
            request.domain(),
            request.moderationMode(),
            request.isActive(),
            request.widgetStyle() != null ? request.widgetStyle().toDomainOrNull() : null
        ));
    }

    @PutMapping("/{siteId}/allowed-origins")
    SiteResponse replaceAllowedOrigins(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable UUID siteId,
        @Valid @RequestBody ReplaceAllowedOriginsRequest request
    ) {
        return SiteResponse.from(siteService.replaceAllowedOrigins(currentUser, siteId, request.allowedOrigins()));
    }

    @GetMapping("/{siteId}/embed-code")
    EmbedCodeResponse getEmbedCode(@CurrentUser AuthenticatedUser currentUser, @PathVariable UUID siteId) {
        return EmbedCodeResponse.from(siteService.getEmbedCode(currentUser, siteId));
    }

    @DeleteMapping("/{siteId}")
    ResponseEntity<Void> deleteSite(@CurrentUser AuthenticatedUser currentUser, @PathVariable UUID siteId) {
        siteService.deleteSite(currentUser, siteId);
        return ResponseEntity.noContent().build();
    }
}
