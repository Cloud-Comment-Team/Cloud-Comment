package com.cloudcomment.privacy.api;

import com.cloudcomment.privacy.application.ConsentRequirements;

public record ConsentRequirementsResponse(
    String privacyPolicyVersion,
    String termsVersion,
    String privacyPolicyUrl,
    String termsUrl,
    String personalDataNoticeUrl,
    String dataExportInfoUrl
) {

    static ConsentRequirementsResponse from(ConsentRequirements requirements) {
        return new ConsentRequirementsResponse(
            requirements.privacyPolicyVersion(),
            requirements.termsVersion(),
            requirements.privacyPolicyUrl(),
            requirements.termsUrl(),
            requirements.personalDataNoticeUrl(),
            requirements.dataExportInfoUrl()
        );
    }
}
