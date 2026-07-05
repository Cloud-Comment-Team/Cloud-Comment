package com.cloudcomment.site.application;

import com.cloudcomment.access.application.ResourceOwnershipService;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.comment.application.AutoModerationDecision;
import com.cloudcomment.comment.application.AutoModerationService;
import com.cloudcomment.comment.domain.CommentStatus;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.ModerationMode;
import com.cloudcomment.site.domain.Site;
import com.cloudcomment.site.domain.SiteInputRules;
import com.cloudcomment.site.domain.AutoModerationSettings;
import com.cloudcomment.site.domain.AutoModerationStrictness;
import com.cloudcomment.site.domain.WidgetStyle;
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
    private final AutoModerationService autoModerationService;

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
        return createSite(currentUser, name, domain, moderationMode, allowedOrigins, WidgetStyle.defaultStyle());
    }

    @Transactional
    public Site createSite(
        AuthenticatedUser currentUser,
        String name,
        String domain,
        ModerationMode moderationMode,
        List<String> allowedOrigins,
        WidgetStyle widgetStyle
    ) {
        return createSite(currentUser, name, domain, moderationMode, allowedOrigins, widgetStyle, null);
    }

    @Transactional
    public Site createSite(
        AuthenticatedUser currentUser,
        String name,
        String domain,
        ModerationMode moderationMode,
        List<String> allowedOrigins,
        WidgetStyle widgetStyle,
        AutoModerationSettings autoModeration
    ) {
        String normalizedName = normalizeName(name);
        String normalizedDomain = normalizeDomain(domain);
        List<String> normalizedOrigins = normalizeOrigins(allowedOrigins);
        AutoModerationSettings normalizedAutoModeration = normalizeAutoModeration(autoModeration);
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
                    widgetStyle != null ? widgetStyle : WidgetStyle.defaultStyle(),
                    normalizedAutoModeration,
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
        return updateSite(currentUser, siteId, name, domain, moderationMode, active, null);
    }

    @Transactional
    public Site updateSite(
        AuthenticatedUser currentUser,
        UUID siteId,
        String name,
        String domain,
        ModerationMode moderationMode,
        Boolean active,
        WidgetStyle widgetStyle
    ) {
        return updateSite(currentUser, siteId, name, domain, moderationMode, active, widgetStyle, null);
    }

    @Transactional
    public Site updateSite(
        AuthenticatedUser currentUser,
        UUID siteId,
        String name,
        String domain,
        ModerationMode moderationMode,
        Boolean active,
        WidgetStyle widgetStyle,
        AutoModerationSettings autoModeration
    ) {
        resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        String normalizedName = name != null ? normalizeName(name) : null;
        String normalizedDomain = domain != null ? normalizeDomain(domain) : null;
        AutoModerationSettings normalizedAutoModeration = autoModeration != null
            ? normalizeAutoModeration(autoModeration)
            : null;
        if (normalizedDomain != null
            && siteRepository.existsByOwnerIdAndDomainExcludingSite(currentUser.id(), normalizedDomain, siteId)) {
            throw duplicateDomain();
        }

        SiteUpdate update = new SiteUpdate(
            normalizedName,
            normalizedDomain,
            moderationMode,
            active,
            widgetStyle,
            normalizedAutoModeration
        );
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

    @Transactional(readOnly = true)
    public AutoModerationDecision checkAutoModeration(
        AuthenticatedUser currentUser,
        UUID siteId,
        String content
    ) {
        resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        Site site = siteRepository.findById(siteId).orElseThrow(this::notFound);
        String normalizedContent = normalizeCommentContent(content);
        return autoModerationService.review(
            normalizedContent,
            site.autoModeration(),
            initialCommentStatus(site.moderationMode())
        );
    }

    @Transactional
    public void deleteSite(AuthenticatedUser currentUser, UUID siteId) {
        resourceOwnershipService.assertSiteOwnedBy(currentUser, siteId);
        if (!siteRepository.deleteById(siteId)) {
            throw notFound();
        }
    }

    private String normalizeName(String name) {
        String normalizedName = name.trim();
        if (normalizedName.isBlank()) {
            throw badRequest("Site name must not be blank");
        }
        return normalizedName;
    }

    private String normalizeCommentContent(String content) {
        if (content == null) {
            throw badRequest("Comment content must not be blank");
        }
        String normalizedContent = content.trim();
        if (normalizedContent.isBlank()) {
            throw badRequest("Comment content must not be blank");
        }
        return normalizedContent;
    }

    private CommentStatus initialCommentStatus(ModerationMode moderationMode) {
        return moderationMode == ModerationMode.PRE_MODERATION
            ? CommentStatus.PENDING
            : CommentStatus.APPROVED;
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

    private AutoModerationSettings normalizeAutoModeration(AutoModerationSettings settings) {
        AutoModerationSettings source = settings != null ? settings : AutoModerationSettings.defaultSettings();
        List<String> blockedWords = source.blockedWords().stream()
            .map(String::trim)
            .filter(word -> !word.isBlank())
            .distinct()
            .toList();

        if (blockedWords.size() > AutoModerationSettings.MAX_BLOCKED_WORDS) {
            throw badRequest("Too many blocked words");
        }
        if (blockedWords.stream().anyMatch(word -> word.length() > AutoModerationSettings.MAX_BLOCKED_WORD_LENGTH)) {
            throw badRequest("Blocked word is too long");
        }
        if (source.maxLinks() < 0 || source.maxLinks() > AutoModerationSettings.MAX_LINKS_LIMIT) {
            throw badRequest("Invalid maximum links value");
        }

        AutoModerationStrictness strictness = source.enabled()
            ? source.strictness()
            : AutoModerationStrictness.OFF;
        return new AutoModerationSettings(
            source.enabled(),
            strictness,
            blockedWords,
            source.holdLinks(),
            source.blockLinks(),
            source.maxLinks()
        );
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
