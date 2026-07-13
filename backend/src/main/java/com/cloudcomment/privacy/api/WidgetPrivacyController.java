package com.cloudcomment.privacy.api;

import com.cloudcomment.privacy.application.ConsentService;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.shared.web.security.PublicApi;
import com.cloudcomment.shared.web.security.WidgetRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@PublicApi
@RestController
@RequestMapping("/api/public/sites/{siteId}/privacy")
class WidgetPrivacyController {

    private final ConsentService consentService;

    WidgetPrivacyController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping("/consent-requirements")
    ConsentRequirementsResponse consentRequirements(
        @PathVariable UUID siteId,
        HttpServletRequest request
    ) {
        WidgetRequestContext context = WidgetRequestContext.require(request);
        if (!context.siteId().equals(siteId)) {
            throw new ApplicationException(
                ApiErrorCode.INVALID_WIDGET_CONTEXT,
                "Invalid widget context"
            );
        }
        return ConsentRequirementsResponse.from(consentService.currentRequirements());
    }
}
