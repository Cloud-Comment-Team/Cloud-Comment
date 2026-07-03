package com.cloudcomment.privacy.application;

import com.cloudcomment.privacy.domain.ConsentSource;
import com.cloudcomment.privacy.persistence.UserConsentRepository;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ConsentService {

    private final UserConsentRepository userConsentRepository;
    private final PrivacyProperties privacyProperties;
    private final Clock clock;

    public ConsentService(
        UserConsentRepository userConsentRepository,
        PrivacyProperties privacyProperties,
        Clock clock
    ) {
        this.userConsentRepository = userConsentRepository;
        this.privacyProperties = privacyProperties;
        this.clock = clock;
    }

    public ConsentRequirements currentRequirements() {
        return new ConsentRequirements(
            privacyProperties.privacyPolicyVersion(),
            privacyProperties.termsVersion(),
            privacyProperties.privacyPolicyUrl(),
            privacyProperties.termsUrl(),
            privacyProperties.personalDataNoticeUrl(),
            privacyProperties.dataExportInfoUrl()
        );
    }

    public void validate(RegistrationConsent consent) {
        if (!privacyProperties.privacyPolicyVersion().equals(consent.privacyPolicyVersion())) {
            throw new ApplicationException(
                ApiErrorCode.VALIDATION_FAILED,
                "Privacy policy version is outdated"
            );
        }
        if (!privacyProperties.termsVersion().equals(consent.termsVersion())) {
            throw new ApplicationException(
                ApiErrorCode.VALIDATION_FAILED,
                "Terms version is outdated"
            );
        }
    }

    @Transactional
    public void recordConsent(UUID userId, RegistrationConsent consent, ConsentSource source) {
        Instant acceptedAt = clock.instant();
        userConsentRepository.save(
            userId,
            consent.privacyPolicyVersion(),
            consent.termsVersion(),
            source,
            acceptedAt
        );
    }
}
