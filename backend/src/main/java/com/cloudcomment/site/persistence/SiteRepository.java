package com.cloudcomment.site.persistence;

import com.cloudcomment.site.application.SitePage;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteRepository {

    SitePage findByOwnerId(UUID ownerId, int page, int pageSize);

    Optional<Site> findById(UUID siteId);

    Site create(
        UUID ownerId,
        String name,
        String domain,
        String publicKey,
        ModerationMode moderationMode,
        List<String> allowedOrigins
    );

    Optional<Site> update(UUID siteId, SiteUpdate update);

    Optional<Site> replaceAllowedOrigins(UUID siteId, List<String> allowedOrigins);

    boolean existsByOwnerIdAndDomain(UUID ownerId, String domain);

    boolean existsByOwnerIdAndDomainExcludingSite(UUID ownerId, String domain, UUID siteId);
}
