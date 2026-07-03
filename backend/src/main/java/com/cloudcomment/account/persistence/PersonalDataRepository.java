package com.cloudcomment.account.persistence;

import com.cloudcomment.account.application.PersonalDataSnapshot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PersonalDataRepository {

    Optional<PersonalDataSnapshot> findByUserId(UUID userId, Instant now);
}
