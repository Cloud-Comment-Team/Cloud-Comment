package com.cloudcomment.privacy.application;

public final class ConsentTestSupport {

    public static final String PRIVACY_POLICY_VERSION = "2026-07-01";
    public static final String TERMS_VERSION = "2026-07-01";

    private ConsentTestSupport() {
    }

    public static RegistrationConsent validConsent() {
        return new RegistrationConsent(true, true, PRIVACY_POLICY_VERSION, TERMS_VERSION);
    }

    public static String registerRequestJson(String email, String password) {
        return """
            {
              "email": "%s",
              "password": "%s",
              "acceptedPrivacyPolicy": true,
              "acceptedTerms": true,
              "privacyPolicyVersion": "%s",
              "termsVersion": "%s"
            }
            """.formatted(email, password, PRIVACY_POLICY_VERSION, TERMS_VERSION);
    }

    public static String registerRequestJsonWithoutConsent(String email, String password) {
        return """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);
    }
}
