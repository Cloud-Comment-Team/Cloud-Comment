package com.cloudcomment.auth.application;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.auth.domain.SessionAudience;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
public class LoginService {

    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SessionTokenHasher sessionTokenHasher;
    private final SecureRandom secureRandom;

    public LoginService(
        UserAccountRepository userAccountRepository,
        PasswordEncoder passwordEncoder,
        Clock clock,
        SessionTokenHasher sessionTokenHasher
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.sessionTokenHasher = sessionTokenHasher;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public LoginResult login(String email, String password, SessionAudience audience) {
        return createSession(email, password, audience, null);
    }

    @Transactional
    public LoginResult loginReplacing(
        String email,
        String password,
        SessionAudience audience,
        String previousToken
    ) {
        return createSession(email, password, audience, previousToken);
    }

    private LoginResult createSession(
        String email,
        String password,
        SessionAudience audience,
        String previousToken
    ) {
        if (audience == SessionAudience.LEGACY) {
            throw new IllegalArgumentException("New sessions must have an explicit non-legacy audience");
        }
        String normalizedEmail = normalizeEmail(email);
        UserCredentials user = userAccountRepository.findCredentialsByEmail(normalizedEmail)
            .filter(UserCredentials::enabled)
            .filter(credentials -> passwordEncoder.matches(password, credentials.passwordHash()))
            .orElseThrow(this::invalidCredentials);

        String token = generateToken();
        Instant expiresAt = clock.instant().plus(SESSION_TTL);
        userAccountRepository.createSession(user.id(), sessionTokenHasher.hash(token), audience, expiresAt);

        if (previousToken != null && !previousToken.isBlank()) {
            userAccountRepository.revokeSession(sessionTokenHasher.hash(previousToken), audience, clock.instant());
        }

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

    private ApplicationException invalidCredentials() {
        return new ApplicationException(
            ApiErrorCode.INVALID_CREDENTIALS,
            "Invalid email or password"
        );
    }
}
