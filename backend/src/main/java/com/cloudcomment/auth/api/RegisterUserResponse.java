package com.cloudcomment.auth.api;

import com.cloudcomment.auth.application.RegisteredUser;

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

    public static RegisterUserResponse from(RegisteredUser user) {
        return new RegisterUserResponse(
            user.id(),
            user.email(),
            user.roles(),
            user.createdAt(),
            user.updatedAt()
        );
    }
}
