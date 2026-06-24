package com.cloudcomment.api.auth;

import com.cloudcomment.service.AuthenticatedUser;

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

    static UserProfileResponse from(AuthenticatedUser user) {
        return new UserProfileResponse(
            user.id(),
            user.email(),
            user.roles(),
            user.createdAt(),
            user.updatedAt()
        );
    }
}
