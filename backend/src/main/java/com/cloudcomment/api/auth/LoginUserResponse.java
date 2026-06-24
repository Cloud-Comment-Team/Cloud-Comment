package com.cloudcomment.api.auth;

import com.cloudcomment.service.LoginResult;

import java.time.Instant;

public record LoginUserResponse(
    String token,
    String tokenType,
    Instant expiresAt,
    UserProfileResponse user
) {

    static LoginUserResponse from(LoginResult result) {
        return new LoginUserResponse(
            result.token(),
            result.tokenType(),
            result.expiresAt(),
            UserProfileResponse.from(result.user())
        );
    }
}
