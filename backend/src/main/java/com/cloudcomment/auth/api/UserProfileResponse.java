package com.cloudcomment.auth.api;

import com.cloudcomment.auth.application.AuthenticatedUser;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt
) {

    public static UserProfileResponse from(AuthenticatedUser user) {
        return new UserProfileResponse(
            user.id(),
            user.email(),
            user.roles(),
            user.createdAt(),
            user.updatedAt()
        );
    }
}
