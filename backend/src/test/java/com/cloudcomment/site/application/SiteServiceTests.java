package com.cloudcomment.site.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.access.domain.OwnedResourceType;
import com.cloudcomment.access.persistence.ResourceOwnershipRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;
import com.cloudcomment.site.persistence.SiteRepository;
import com.cloudcomment.site.persistence.SiteUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteServiceTests {

    private static final Instant TIMESTAMP = Instant.parse("2026-06-28T12:00:00Z");
    private static final String PUBLIC_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void createSiteNormalizesInputDeduplicatesOriginsAndGeneratesPublicKey() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, true);
        AuthenticatedUser currentUser = currentUser();

        Site site = service.createSite(
            currentUser,
            " Example site ",
            " Example.COM ",
            ModerationMode.PRE_MODERATION,
            List.of("https://Example.com", "https://example.com")
        );

        assertThat(repository.createdOwnerId).isEqualTo(currentUser.id());
        assertThat(repository.createdName).isEqualTo("Example site");
        assertThat(repository.createdDomain).isEqualTo("example.com");
        assertThat(repository.createdPublicKey).isEqualTo(PUBLIC_KEY);
        assertThat(repository.createdAllowedOrigins).containsExactly("https://example.com");
        assertThat(site.publicKey()).isEqualTo(PUBLIC_KEY);
    }

    @Test
    void createSiteRejectsDuplicateOwnerDomain() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        repository.existsByOwnerAndDomain = true;
        SiteService service = service(repository, true);

        assertThatThrownBy(() -> service.createSite(
            currentUser(),
            "Example site",
            "example.com",
            ModerationMode.PRE_MODERATION,
            List.of("https://example.com")
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Site domain already exists")
            .extracting("code")
            .hasToString("BUSINESS_ERROR");
    }

    @Test
    void getSiteMasksForeignOrMissingSiteAsNotFound() {
        SiteService service = service(new CapturingSiteRepository(), false);

        assertThatThrownBy(() -> service.getSite(currentUser(), UUID.randomUUID()))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    @Test
    void updateSiteChecksOwnershipAndRejectsDuplicateDomain() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        repository.existsByOwnerAndDomainExcludingSite = true;
        SiteService service = service(repository, true);

        assertThatThrownBy(() -> service.updateSite(
            currentUser(),
            UUID.randomUUID(),
            null,
            "other.example.com",
            null,
            null
        ))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Site domain already exists")
            .extracting("code")
            .hasToString("BUSINESS_ERROR");
    }

    @Test
    void replaceAllowedOriginsChecksOwnershipAndNormalizesOrigins() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, true);
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();

        service.replaceAllowedOrigins(currentUser, siteId, List.of("HTTP://LOCALHOST:3000", "http://localhost:3000"));

        assertThat(repository.replacedSiteId).isEqualTo(siteId);
        assertThat(repository.replacedAllowedOrigins).containsExactly("http://localhost:3000");
    }

    @Test
    void getEmbedCodeChecksOwnershipBeforeBuildingSnippet() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, true);
        AuthenticatedUser currentUser = currentUser();
        UUID siteId = UUID.randomUUID();

        EmbedCode embedCode = service.getEmbedCode(currentUser, siteId);

        assertThat(embedCode.siteId()).isEqualTo(siteId);
        assertThat(embedCode.embedCode()).contains("data-site-id=\"" + siteId + "\"");
    }

    private SiteService service(CapturingSiteRepository repository, boolean owned) {
        ResourceOwnershipRepository ownershipRepository = (UUID ownerId, OwnedResourceType resourceType, UUID resourceId) -> owned;
        return new SiteService(
            repository,
            new ResourceOwnershipService(ownershipRepository),
            new FixedPublicKeyGenerator(),
            new EmbedCodeService(new EmbedCodeProperties(
                "http://localhost/widget/cloud-comment-widget.js",
                "http://localhost/api"
            ))
        );
    }

    private AuthenticatedUser currentUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of("OWNER"), TIMESTAMP, TIMESTAMP);
    }

    private static class FixedPublicKeyGenerator extends PublicKeyGenerator {

        @Override
        String generate() {
            return PUBLIC_KEY;
        }
    }

    private static class CapturingSiteRepository implements SiteRepository {

        private boolean existsByOwnerAndDomain;
        private boolean existsByOwnerAndDomainExcludingSite;
        private UUID createdOwnerId;
        private String createdName;
        private String createdDomain;
        private String createdPublicKey;
        private List<String> createdAllowedOrigins;
        private UUID replacedSiteId;
        private List<String> replacedAllowedOrigins;

        @Override
        public SitePage findByOwnerId(UUID ownerId, int page, int pageSize) {
            return new SitePage(List.of(), page, pageSize, 0);
        }

        @Override
        public Optional<Site> findById(UUID siteId) {
            return Optional.of(site(siteId, UUID.randomUUID(), "example.com", List.of("https://example.com")));
        }

        @Override
        public Site create(
            UUID ownerId,
            String name,
            String domain,
            String publicKey,
            ModerationMode moderationMode,
            List<String> allowedOrigins
        ) {
            if (existsByOwnerAndDomain) {
                throw new DuplicateKeyException("duplicate domain");
            }
            createdOwnerId = ownerId;
            createdName = name;
            createdDomain = domain;
            createdPublicKey = publicKey;
            createdAllowedOrigins = List.copyOf(allowedOrigins);
            return site(UUID.randomUUID(), ownerId, domain, allowedOrigins);
        }

        @Override
        public Optional<Site> update(UUID siteId, SiteUpdate update) {
            return Optional.of(site(siteId, UUID.randomUUID(), update.domain(), List.of("https://example.com")));
        }

        @Override
        public Optional<Site> replaceAllowedOrigins(UUID siteId, List<String> allowedOrigins) {
            replacedSiteId = siteId;
            replacedAllowedOrigins = List.copyOf(allowedOrigins);
            return Optional.of(site(siteId, UUID.randomUUID(), "example.com", allowedOrigins));
        }

        @Override
        public boolean existsByOwnerIdAndDomain(UUID ownerId, String domain) {
            return existsByOwnerAndDomain;
        }

        @Override
        public boolean existsByOwnerIdAndDomainExcludingSite(UUID ownerId, String domain, UUID siteId) {
            return existsByOwnerAndDomainExcludingSite;
        }

        private Site site(UUID siteId, UUID ownerId, String domain, List<String> allowedOrigins) {
            return new Site(
                siteId,
                ownerId,
                "Example site",
                domain,
                PUBLIC_KEY,
                ModerationMode.PRE_MODERATION,
                true,
                allowedOrigins,
                TIMESTAMP,
                TIMESTAMP
            );
        }
    }
}
