package com.cloudcomment.privacy.persistence;

import com.cloudcomment.privacy.domain.ConsentSource;

import java.time.Instant;
import java.util.UUID;

public interface UserConsentRepository {

    void save(
        UUID userId,
        String privacyPolicyVersion,
        String termsVersion,
        ConsentSource source,
        Instant acceptedAt
    );
}
