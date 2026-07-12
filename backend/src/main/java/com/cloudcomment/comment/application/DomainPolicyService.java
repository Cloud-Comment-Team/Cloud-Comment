package com.cloudcomment.comment.application;

import com.cloudcomment.comment.persistence.PublicCommentRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.SiteInputRules;
import com.cloudcomment.site.application.SiteInstallationHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainPolicyService {

    private final PublicCommentRepository publicCommentRepository;
    private final SiteInstallationHealthService installationHealthService;

    @Transactional(readOnly = true)
    public WidgetSiteAccess validate(UUID siteId, String origin) {
        String normalizedOrigin = SiteInputRules.normalizeOrigin(origin).orElseThrow(this::notFound);
        return validateNormalized(siteId, normalizedOrigin);
    }

    private WidgetSiteAccess validateNormalized(UUID siteId, String normalizedOrigin) {
        WidgetSite site = publicCommentRepository.findActiveSite(siteId).orElseThrow(this::notFound);
        if (!publicCommentRepository.isAllowedOrigin(site.id(), normalizedOrigin)) {
            throw notFound();
        }
        return new WidgetSiteAccess(
            site.id(),
            site.moderationMode(),
            site.widgetStyle(),
            site.autoModeration(),
            normalizedOrigin
        );
    }

    @Transactional(readOnly = true)
    public boolean isOriginAllowed(UUID siteId, String origin) {
        try {
            validate(siteId, origin);
            return true;
        } catch (ApplicationException exception) {
            return false;
        }
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }

    public void recordSuccessfulInstallation(UUID siteId, String origin) {
        SiteInputRules.normalizeOrigin(origin).ifPresent(normalizedOrigin ->
            recordSuccessfulOrigin(siteId, normalizedOrigin)
        );
    }

    public void recordRejectedInstallation(UUID siteId, String origin) {
        SiteInputRules.normalizeOrigin(origin).ifPresent(normalizedOrigin ->
            recordRejectedOrigin(siteId, normalizedOrigin)
        );
    }

    private void recordRejectedOrigin(UUID siteId, String normalizedOrigin) {
        try {
            installationHealthService.recordRejectedOrigin(siteId, normalizedOrigin);
        } catch (RuntimeException exception) {
            log.warn("Could not record rejected widget origin for site {}", siteId, exception);
        }
    }

    private void recordSuccessfulOrigin(UUID siteId, String normalizedOrigin) {
        try {
            installationHealthService.recordSuccessfulOrigin(siteId, normalizedOrigin);
        } catch (RuntimeException exception) {
            log.warn("Could not record successful widget origin for site {}", siteId, exception);
        }
    }
}
