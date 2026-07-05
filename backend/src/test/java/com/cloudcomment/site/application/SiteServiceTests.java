package com.cloudcomment.site.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.access.domain.OwnedResourceType;
import com.cloudcomment.access.persistence.ResourceOwnershipRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.comment.application.AutoModerationService;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;
import com.cloudcomment.site.domain.WidgetCornerRadius;
import com.cloudcomment.site.domain.WidgetStyle;
import com.cloudcomment.site.domain.WidgetTheme;
import com.cloudcomment.site.persistence.SiteRepository;
import com.cloudcomment.site.persistence.SiteUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

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
        assertThat(repository.createdWidgetStyle).isEqualTo(WidgetStyle.defaultStyle());
        assertThat(repository.createdAutoModeration).isEqualTo(AutoModerationSettings.defaultSettings());
        assertThat(repository.createdAllowedOrigins).containsExactly("https://example.com");
        assertThat(site.publicKey()).isEqualTo(PUBLIC_KEY);
    }

    @Test
    void createSitePassesCustomWidgetStyleToRepository() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, true);
        WidgetStyle style = new WidgetStyle(WidgetTheme.DARK, "#ff3366", WidgetCornerRadius.LARGE);

        Site site = service.createSite(
            currentUser(),
            "Example site",
            "example.com",
            ModerationMode.PRE_MODERATION,
            List.of("https://example.com"),
            style
        );

        assertThat(repository.createdWidgetStyle).isEqualTo(style);
        assertThat(site.widgetStyle()).isEqualTo(style);
    }

    @Test
    void createSiteNormalizesCustomAutoModerationSettings() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, true);
        AutoModerationSettings settings = new AutoModerationSettings(
            true,
            AutoModerationStrictness.STRICT,
            List.of(" spam ", "", "spam", "casino"),
            false,
            true,
            0
        );

        Site site = service.createSite(
            currentUser(),
            "Example site",
            "example.com",
            ModerationMode.POST_MODERATION,
            List.of("https://example.com"),
            WidgetStyle.defaultStyle(),
            settings
        );

        assertThat(repository.createdAutoModeration.enabled()).isTrue();
        assertThat(repository.createdAutoModeration.strictness()).isEqualTo(AutoModerationStrictness.STRICT);
        assertThat(repository.createdAutoModeration.blockedWords()).containsExactly("spam", "casino");
        assertThat(repository.createdAutoModeration.holdLinks()).isFalse();
        assertThat(repository.createdAutoModeration.blockLinks()).isTrue();
        assertThat(repository.createdAutoModeration.maxLinks()).isZero();
        assertThat(site.autoModeration()).isEqualTo(repository.createdAutoModeration);
    }

    @Test
    void createSiteTurnsStrictnessOffWhenAutoModerationIsDisabled() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, true);

        service.createSite(
            currentUser(),
            "Example site",
            "example.com",
            ModerationMode.POST_MODERATION,
            List.of("https://example.com"),
            WidgetStyle.defaultStyle(),
            new AutoModerationSettings(
                false,
                AutoModerationStrictness.STRICT,
                List.of("spam"),
                true,
                true,
                1
            )
        );

        assertThat(repository.createdAutoModeration.enabled()).isFalse();
        assertThat(repository.createdAutoModeration.strictness()).isEqualTo(AutoModerationStrictness.OFF);
        assertThat(repository.createdAutoModeration.blockedWords()).containsExactly("spam");
    }

    @Test
    void createSiteRejectsInvalidAutoModerationSettings() {
        SiteService service = service(new CapturingSiteRepository(), true);

        assertBadAutoModeration(service, new AutoModerationSettings(
            true,
            AutoModerationStrictness.BALANCED,
            IntStream.rangeClosed(1, AutoModerationSettings.MAX_BLOCKED_WORDS + 1)
                .mapToObj(index -> "word-" + index)
                .toList(),
            true,
            false,
            2
        ));
        assertBadAutoModeration(service, new AutoModerationSettings(
            true,
            AutoModerationStrictness.BALANCED,
            List.of("x".repeat(AutoModerationSettings.MAX_BLOCKED_WORD_LENGTH + 1)),
            true,
            false,
            2
        ));
        assertBadAutoModeration(service, new AutoModerationSettings(
            true,
            AutoModerationStrictness.BALANCED,
            List.of(),
            true,
            false,
            -1
        ));
        assertBadAutoModeration(service, new AutoModerationSettings(
            true,
            AutoModerationStrictness.BALANCED,
            List.of(),
            true,
            false,
            AutoModerationSettings.MAX_LINKS_LIMIT + 1
        ));
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
    void updateSiteWithNoChangesReturnsExistingSiteWithoutWriting() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, true);
        UUID siteId = UUID.randomUUID();

        Site site = service.updateSite(currentUser(), siteId, null, null, null, null, null, null);

        assertThat(site.id()).isEqualTo(siteId);
        assertThat(repository.update).isNull();
    }

    @Test
    void updateSiteNormalizesAutoModerationPatch() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, true);
        UUID siteId = UUID.randomUUID();
        AutoModerationSettings settings = new AutoModerationSettings(
            true,
            AutoModerationStrictness.STRICT,
            List.of(" block ", "block", "review"),
            false,
            false,
            0
        );

        Site site = service.updateSite(
            currentUser(),
            siteId,
            null,
            null,
            null,
            null,
            null,
            settings
        );

        assertThat(repository.update).isNotNull();
        assertThat(repository.update.autoModeration().blockedWords()).containsExactly("block", "review");
        assertThat(repository.update.autoModeration().strictness()).isEqualTo(AutoModerationStrictness.STRICT);
        assertThat(site.autoModeration()).isEqualTo(repository.update.autoModeration());
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

    @Test
    void checkAutoModerationReturnsExplainableDecisionForOwnedSite() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        repository.existingAutoModeration = new AutoModerationSettings(
            true,
            AutoModerationStrictness.BALANCED,
            List.of("casino"),
            true,
            false,
            2
        );
        SiteService service = service(repository, true);

        var decision = service.checkAutoModeration(currentUser(), UUID.randomUUID(), "К-а-з-и-н-о casino");

        assertThat(decision.status()).isEqualTo(CommentStatus.SPAM);
        assertThat(decision.score()).isGreaterThanOrEqualTo(90);
        assertThat(decision.reason()).contains("Автомодерация");
        assertThat(decision.signals())
            .extracting("category")
            .contains("CUSTOM_BLOCKED_WORD", "SPAM_PHRASE");
    }

    @Test
    void checkAutoModerationMasksForeignOrMissingSiteAsNotFound() {
        SiteService service = service(new CapturingSiteRepository(), false);

        assertThatThrownBy(() -> service.checkAutoModeration(currentUser(), UUID.randomUUID(), "casino"))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    @Test
    void deleteSiteChecksOwnershipAndDeletesSite() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, true);
        UUID siteId = UUID.randomUUID();

        service.deleteSite(currentUser(), siteId);

        assertThat(repository.deletedSiteId).isEqualTo(siteId);
    }

    @Test
    void deleteSiteMasksForeignOrMissingSiteAsNotFound() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        SiteService service = service(repository, false);

        assertThatThrownBy(() -> service.deleteSite(currentUser(), UUID.randomUUID()))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
    }

    @Test
    void deleteSiteReturnsNotFoundWhenSiteDoesNotExist() {
        CapturingSiteRepository repository = new CapturingSiteRepository();
        repository.deleteReturnsFalse = true;
        SiteService service = service(repository, true);

        assertThatThrownBy(() -> service.deleteSite(currentUser(), UUID.randomUUID()))
            .isInstanceOf(ApplicationException.class)
            .hasMessage("Resource not found")
            .extracting("code")
            .hasToString("NOT_FOUND");
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
            )),
            new AutoModerationService()
        );
    }

    private void assertBadAutoModeration(SiteService service, AutoModerationSettings settings) {
        assertThatThrownBy(() -> service.createSite(
            currentUser(),
            "Example site",
            "example.com",
            ModerationMode.POST_MODERATION,
            List.of("https://example.com"),
            WidgetStyle.defaultStyle(),
            settings
        ))
            .isInstanceOf(ApplicationException.class)
            .extracting("code")
            .hasToString("BAD_REQUEST");
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
        private WidgetStyle createdWidgetStyle;
        private AutoModerationSettings createdAutoModeration;
        private List<String> createdAllowedOrigins;
        private SiteUpdate update;
        private UUID replacedSiteId;
        private List<String> replacedAllowedOrigins;
        private UUID deletedSiteId;
        private boolean deleteReturnsFalse;
        private AutoModerationSettings existingAutoModeration = AutoModerationSettings.defaultSettings();

        @Override
        public boolean deleteById(UUID siteId) {
            if (deleteReturnsFalse) {
                return false;
            }
            deletedSiteId = siteId;
            return true;
        }

        @Override
        public SitePage findByOwnerId(UUID ownerId, int page, int pageSize) {
            return new SitePage(List.of(), page, pageSize, 0);
        }

        @Override
        public Optional<Site> findById(UUID siteId) {
            return Optional.of(site(
                siteId,
                UUID.randomUUID(),
                "example.com",
                WidgetStyle.defaultStyle(),
                existingAutoModeration,
                List.of("https://example.com")
            ));
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
            return create(ownerId, name, domain, publicKey, moderationMode, WidgetStyle.defaultStyle(), allowedOrigins);
        }

        @Override
        public Site create(
            UUID ownerId,
            String name,
            String domain,
            String publicKey,
            ModerationMode moderationMode,
            WidgetStyle widgetStyle,
            List<String> allowedOrigins
        ) {
            return create(
                ownerId,
                name,
                domain,
                publicKey,
                moderationMode,
                widgetStyle,
                AutoModerationSettings.defaultSettings(),
                allowedOrigins
            );
        }

        @Override
        public Site create(
            UUID ownerId,
            String name,
            String domain,
            String publicKey,
            ModerationMode moderationMode,
            WidgetStyle widgetStyle,
            AutoModerationSettings autoModeration,
            List<String> allowedOrigins
        ) {
            if (existsByOwnerAndDomain) {
                throw new DuplicateKeyException("duplicate domain");
            }
            createdOwnerId = ownerId;
            createdName = name;
            createdDomain = domain;
            createdPublicKey = publicKey;
            createdWidgetStyle = widgetStyle;
            createdAutoModeration = autoModeration;
            createdAllowedOrigins = List.copyOf(allowedOrigins);
            return site(UUID.randomUUID(), ownerId, domain, widgetStyle, autoModeration, allowedOrigins);
        }

        @Override
        public Optional<Site> update(UUID siteId, SiteUpdate update) {
            this.update = update;
            WidgetStyle widgetStyle = update.widgetStyle() != null ? update.widgetStyle() : WidgetStyle.defaultStyle();
            AutoModerationSettings autoModeration = update.autoModeration() != null
                ? update.autoModeration()
                : AutoModerationSettings.defaultSettings();
            String domain = update.domain() != null ? update.domain() : "example.com";
            return Optional.of(site(
                siteId,
                UUID.randomUUID(),
                domain,
                widgetStyle,
                autoModeration,
                List.of("https://example.com")
            ));
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
            return site(siteId, ownerId, domain, WidgetStyle.defaultStyle(), allowedOrigins);
        }

        private Site site(
            UUID siteId,
            UUID ownerId,
            String domain,
            WidgetStyle widgetStyle,
            List<String> allowedOrigins
        ) {
            return site(siteId, ownerId, domain, widgetStyle, AutoModerationSettings.defaultSettings(), allowedOrigins);
        }

        private Site site(
            UUID siteId,
            UUID ownerId,
            String domain,
            WidgetStyle widgetStyle,
            AutoModerationSettings autoModeration,
            List<String> allowedOrigins
        ) {
            return new Site(
                siteId,
                ownerId,
                "Example site",
                domain,
                PUBLIC_KEY,
                ModerationMode.PRE_MODERATION,
                true,
                widgetStyle,
                autoModeration,
                allowedOrigins,
                TIMESTAMP,
                TIMESTAMP
            );
        }
    }
}
