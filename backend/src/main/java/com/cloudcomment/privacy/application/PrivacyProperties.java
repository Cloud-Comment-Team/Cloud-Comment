package com.cloudcomment.privacy.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud-comment.privacy")
public record PrivacyProperties(
    String privacyPolicyVersion,
    String termsVersion,
    String privacyPolicyUrl,
    String termsUrl,
    String personalDataNoticeUrl,
    String dataExportInfoUrl
) {

    public PrivacyProperties {
        if (privacyPolicyVersion == null || privacyPolicyVersion.isBlank()) {
            privacyPolicyVersion = "2026-07-01";
        }
        if (termsVersion == null || termsVersion.isBlank()) {
            termsVersion = "2026-07-01";
        }
        if (privacyPolicyUrl == null || privacyPolicyUrl.isBlank()) {
            privacyPolicyUrl = "/legal/privacy-policy.html";
        }
        if (termsUrl == null || termsUrl.isBlank()) {
            termsUrl = "/legal/terms.html";
        }
        if (personalDataNoticeUrl == null || personalDataNoticeUrl.isBlank()) {
            personalDataNoticeUrl = "/docs/personal-data-notice.md";
        }
        if (dataExportInfoUrl == null || dataExportInfoUrl.isBlank()) {
            dataExportInfoUrl = "/docs/personal-data-notice.md#data-export";
        }
    }
}
