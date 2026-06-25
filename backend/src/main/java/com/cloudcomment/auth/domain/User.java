package com.cloudcomment.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record User(
    UUID id,
    String email,
    String passwordHash,
    String displayName,
    boolean enabled,
    Set<RoleName> roles,
    Instant createdAt,
    Instant updatedAt
) {

    public User {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
