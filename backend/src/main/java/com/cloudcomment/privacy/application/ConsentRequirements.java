package com.cloudcomment.privacy.application;

public record ConsentRequirements(
    String privacyPolicyVersion,
    String termsVersion,
    String privacyPolicyUrl,
    String termsUrl,
    String personalDataNoticeUrl,
    String dataExportInfoUrl
) {
}
