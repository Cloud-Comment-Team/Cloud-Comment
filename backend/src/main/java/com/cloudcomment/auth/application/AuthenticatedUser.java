package com.cloudcomment.auth.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
    UUID id,
    String email,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt
) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
    }
}
