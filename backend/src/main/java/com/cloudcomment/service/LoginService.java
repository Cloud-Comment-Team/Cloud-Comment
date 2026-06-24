package com.cloudcomment.service;

import com.cloudcomment.api.error.ApiErrorCode;
import com.cloudcomment.api.error.ApiException;
import com.cloudcomment.persistence.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class LoginService {

    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public LoginService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public LoginResult login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        UserCredentials user = userAccountRepository.findCredentialsByEmail(normalizedEmail)
            .filter(UserCredentials::enabled)
            .filter(credentials -> passwordEncoder.matches(password, credentials.passwordHash()))
            .orElseThrow(this::invalidCredentials);

        String token = generateToken();
        Instant expiresAt = clock.instant().plus(SESSION_TTL);
        userAccountRepository.createSession(user.id(), hashToken(token), expiresAt);

        return new LoginResult(
            token,
            "Bearer",
            expiresAt,
            new AuthenticatedUser(user.id(), user.email(), user.roles(), user.createdAt(), user.updatedAt())
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(
            ApiErrorCode.INVALID_CREDENTIALS,
            HttpStatus.UNAUTHORIZED,
            "Invalid email or password"
        );
    }
}
