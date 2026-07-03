package com.cloudcomment.account.persistence;

import com.cloudcomment.account.application.RelatedPersonalDataAnonymization;

import java.time.Instant;
import java.util.UUID;

public interface AccountLifecycleRepository {

    RelatedPersonalDataAnonymization anonymizeRelatedPersonalData(UUID userId, Instant anonymizedAt);

    int deleteInactiveSessionsBefore(Instant cutoff);
}
