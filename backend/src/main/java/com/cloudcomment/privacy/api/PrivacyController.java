package com.cloudcomment.privacy.api;

import com.cloudcomment.privacy.application.ConsentRequirements;
import com.cloudcomment.privacy.application.ConsentService;
import com.cloudcomment.shared.web.security.PublicApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/privacy")
class PrivacyController {

    private final ConsentService consentService;

    PrivacyController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @PublicApi
    @CrossOrigin(origins = "*")
    @GetMapping("/consent-requirements")
    ConsentRequirementsResponse consentRequirements() {
        return ConsentRequirementsResponse.from(consentService.currentRequirements());
    }
}
