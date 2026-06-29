package com.cloudcomment.auth.api;

import com.cloudcomment.auth.application.LoginResult;

import java.time.Instant;

public record LoginUserResponse(
    String token,
    String tokenType,
    Instant expiresAt,
    UserProfileResponse user
) {

    public static LoginUserResponse from(LoginResult result) {
        return new LoginUserResponse(
            result.token(),
            result.tokenType(),
            result.expiresAt(),
            UserProfileResponse.from(result.user())
        );
    }
}
