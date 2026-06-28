package com.cloudcomment.site.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;
import com.cloudcomment.site.domain.SiteInputRules;
import com.cloudcomment.site.persistence.SiteRepository;
import com.cloudcomment.site.persistence.SiteUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SiteService {

    private static final int PUBLIC_KEY_CREATE_ATTEMPTS = 5;

    private final SiteRepository siteRepository;
    private final ResourceOwnershipService resourceOwnershipService;
    private final PublicKeyGenerator publicKeyGenerator;
    private final EmbedCodeService embedCodeService;

    @Transactional(readOnly = true)
    public SitePage listSites(AuthenticatedUser currentUser, int page, int pageSize) {
        return siteRepository.findByOwnerId(currentUser.id(), page, pageSize);
    }

    @Transactional
    public Site createSite(
        AuthenticatedUser currentUser,
        String name,
        String domain,
        ModerationMode moderationMode,
        List<String> allowedOrigins
    ) {
        String normalizedName = normalizeName(name);
        String normalizedDomain = normalizeDomain(domain);
        List<String> normalizedOrigins = normalizeOrigins(allowedOrigins);
        if (siteRepository.existsByOwnerIdAndDomain(currentUser.id(), normalizedDomain)) {
            throw duplicateDomain();
        }

        for (int attempt = 0; attempt < PUBLIC_KEY_CREATE_ATTEMPTS; attempt++) {
            try {
                return siteRepository.create(
                    currentUser.id(),
                    normalizedName,
                    normalizedDomain,
                    publicKeyGenerator.generate(),
                    moderationMode,
                    normalizedOrigins
                );
            } catch (DuplicateKeyException exception) {
                if (siteRepository.existsByOwnerIdAndDomain(currentUser.id(), normalizedDomain)) {
                    throw duplicateDomain();
                }
            }
        }

        throw new ApplicationException(ApiErrorCode.INTERNAL_ERROR, "Could not create site");
    }

    @Transactional(readOnly = true)
    public Site getSite(AuthenticatedUser currentUser, UUID siteId) {
        resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        return siteRepository.findById(siteId).orElseThrow(this::notFound);
    }

    @Transactional
    public Site updateSite(
        AuthenticatedUser currentUser,
        UUID siteId,
        String name,
        String domain,
        ModerationMode moderationMode,
        Boolean active
    ) {
        resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        String normalizedName = name != null ? normalizeName(name) : null;
        String normalizedDomain = domain != null ? normalizeDomain(domain) : null;
        if (normalizedDomain != null
            && siteRepository.existsByOwnerIdAndDomainExcludingSite(currentUser.id(), normalizedDomain, siteId)) {
            throw duplicateDomain();
        }

        SiteUpdate update = new SiteUpdate(normalizedName, normalizedDomain, moderationMode, active);
        if (!update.hasChanges()) {
            return siteRepository.findById(siteId).orElseThrow(this::notFound);
        }

        try {
            return siteRepository.update(siteId, update).orElseThrow(this::notFound);
        } catch (DuplicateKeyException exception) {
            throw duplicateDomain();
        }
    }

    @Transactional
    public Site replaceAllowedOrigins(
        AuthenticatedUser currentUser,
        UUID siteId,
        List<String> allowedOrigins
    ) {
        resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        return siteRepository.replaceAllowedOrigins(siteId, normalizeOrigins(allowedOrigins))
            .orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public EmbedCode getEmbedCode(AuthenticatedUser currentUser, UUID siteId) {
        resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        return embedCodeService.build(siteId);
    }

    private String normalizeName(String name) {
        String normalizedName = name.trim();
        if (normalizedName.isBlank()) {
            throw badRequest("Site name must not be blank");
        }
        return normalizedName;
    }

    private String normalizeDomain(String domain) {
        return SiteInputRules.normalizeDomain(domain)
            .orElseThrow(() -> badRequest("Invalid site domain"));
    }

    private List<String> normalizeOrigins(List<String> origins) {
        List<String> normalizedOrigins = SiteInputRules.normalizeOrigins(origins);
        if (normalizedOrigins.isEmpty()) {
            throw badRequest("Allowed origins must not be empty");
        }
        return normalizedOrigins;
    }

    private ApplicationException duplicateDomain() {
        return new ApplicationException(ApiErrorCode.BUSINESS_ERROR, "Site domain already exists");
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }

    private ApplicationException badRequest(String message) {
        return new ApplicationException(ApiErrorCode.BAD_REQUEST, message);
    }
}
