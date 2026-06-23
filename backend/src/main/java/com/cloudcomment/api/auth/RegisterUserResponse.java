package com.cloudcomment.api.auth;

import com.cloudcomment.service.RegisteredUser;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RegisterUserResponse(
    UUID id,
    String email,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt
) {

    static RegisterUserResponse from(RegisteredUser user) {
        return new RegisterUserResponse(
            user.id(),
            user.email(),
            user.roles(),
            user.createdAt(),
            user.updatedAt()
        );
    }
}
