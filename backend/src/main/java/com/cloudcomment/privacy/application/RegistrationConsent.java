package com.cloudcomment.privacy.application;

import com.cloudcomment.auth.api.RegisterUserRequest;

public record RegistrationConsent(
    boolean acceptedPrivacyPolicy,
    boolean acceptedTerms,
    String privacyPolicyVersion,
    String termsVersion
) {

    public static RegistrationConsent from(RegisterUserRequest request) {
        return new RegistrationConsent(
            request.acceptedPrivacyPolicy(),
            request.acceptedTerms(),
            request.privacyPolicyVersion(),
            request.termsVersion()
        );
    }
}
