package com.cloudcomment.auth.api;

import com.cloudcomment.auth.application.LoginResult;

import java.time.Instant;

public record AdminLoginResponse(
    Instant expiresAt,
    UserProfileResponse user
) {

    public static AdminLoginResponse from(LoginResult result) {
        return new AdminLoginResponse(result.expiresAt(), UserProfileResponse.from(result.user()));
    }
}
