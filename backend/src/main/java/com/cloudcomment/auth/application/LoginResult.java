package com.cloudcomment.auth.application;

import java.time.Instant;

public record LoginResult(
    String token,
    String tokenType,
    Instant expiresAt,
    AuthenticatedUser user
) {
}
