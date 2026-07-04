package com.cloudcomment.comment.application;

import com.cloudcomment.comment.persistence.PublicCommentRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.site.domain.SiteInputRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DomainPolicyService {

    private final PublicCommentRepository publicCommentRepository;

    @Transactional(readOnly = true)
    public WidgetSiteAccess validate(UUID siteId, String origin) {
        String normalizedOrigin = SiteInputRules.normalizeOrigin(origin).orElseThrow(this::notFound);
        WidgetSite site = publicCommentRepository.findActiveSite(siteId).orElseThrow(this::notFound);
        if (!publicCommentRepository.isAllowedOrigin(site.id(), normalizedOrigin)) {
            throw notFound();
        }
        return new WidgetSiteAccess(site.id(), site.moderationMode(), site.widgetStyle(), normalizedOrigin);
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
}
