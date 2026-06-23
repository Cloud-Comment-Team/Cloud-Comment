package com.cloudcomment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserRole(
    UUID userId,
    RoleName role,
    Instant createdAt
) {

    public UserRole {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
