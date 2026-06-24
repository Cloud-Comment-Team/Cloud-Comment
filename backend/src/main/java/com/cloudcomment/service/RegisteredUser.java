package com.cloudcomment.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RegisteredUser(
    UUID id,
    String email,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt
) {

    public RegisteredUser {
        roles = Set.copyOf(roles);
    }
}
