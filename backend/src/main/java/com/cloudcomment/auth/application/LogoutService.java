package com.cloudcomment.auth.application;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.persistence.SessionRevocationResult;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.auth.domain.SessionAudience;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class LogoutService {

    private final UserAccountRepository userAccountRepository;
    private final SessionTokenHasher sessionTokenHasher;
    private final Clock clock;

    public LogoutService(
        UserAccountRepository userAccountRepository,
        SessionTokenHasher sessionTokenHasher,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.sessionTokenHasher = sessionTokenHasher;
        this.clock = clock;
    }

    @Transactional
    public void logout(String token, SessionAudience audience) {
        SessionRevocationResult result = userAccountRepository.revokeSession(
            sessionTokenHasher.hash(token),
            audience,
            clock.instant()
        );

        if (result == SessionRevocationResult.NOT_FOUND_OR_EXPIRED) {
            throw invalidSession();
        }
    }

    @Transactional
    public void logoutIfPresent(String token, SessionAudience audience) {
        userAccountRepository.revokeSession(
            sessionTokenHasher.hash(token),
            audience,
            clock.instant()
        );
    }

    private ApplicationException invalidSession() {
        return new ApplicationException(
            ApiErrorCode.INVALID_SESSION,
            "Invalid or expired session"
        );
    }
}
