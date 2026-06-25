package com.cloudcomment.auth.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserCredentials(
    UUID id,
    String email,
    String passwordHash,
    boolean enabled,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt
) {

    public UserCredentials {
        roles = Set.copyOf(roles);
    }
}
