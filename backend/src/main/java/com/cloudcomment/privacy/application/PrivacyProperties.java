package com.cloudcomment.privacy.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud-comment.privacy")
public record PrivacyProperties(
    String privacyPolicyVersion,
    String termsVersion,
    String privacyPolicyUrl,
    String termsUrl,
    String personalDataNoticeUrl,
    String dataExportInfoUrl,
    int sessionRetentionDays,
    int deletionRequestRetentionDays,
    String retentionCleanupCron
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
            personalDataNoticeUrl = "/legal/personal-data-notice.html";
        }
        if (dataExportInfoUrl == null || dataExportInfoUrl.isBlank()) {
            dataExportInfoUrl = "/legal/personal-data-notice.html#data-export";
        }
        if (sessionRetentionDays <= 0) {
            sessionRetentionDays = 30;
        }
        if (deletionRequestRetentionDays <= 0) {
            deletionRequestRetentionDays = 30;
        }
        if (retentionCleanupCron == null || retentionCleanupCron.isBlank()) {
            retentionCleanupCron = "0 0 3 * * *";
        }
    }
}
