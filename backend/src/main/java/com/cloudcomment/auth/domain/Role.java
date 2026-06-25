package com.cloudcomment.auth.domain;

import java.time.Instant;
import java.util.Objects;

public record Role(
    RoleName name,
    String description,
    Instant createdAt
) {

    public Role {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
