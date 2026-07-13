package com.cloudcomment.auth.application;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.persistence.SessionRevocationResult;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.auth.domain.SessionAudience;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

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
        if (audience == SessionAudience.WIDGET) {
            throw invalidSession();
        }
        SessionRevocationResult result = userAccountRepository.revokeSession(
            sessionTokenHasher.hash(token),
            audience,
            null,
            null,
            clock.instant()
        );

        if (result == SessionRevocationResult.NOT_FOUND_OR_EXPIRED) {
            throw invalidSession();
        }
    }

    @Transactional
    public void logoutIfPresent(String token, SessionAudience audience) {
        if (audience == SessionAudience.WIDGET) {
            return;
        }
        userAccountRepository.revokeSession(
            sessionTokenHasher.hash(token),
            audience,
            null,
            null,
            clock.instant()
        );
    }

    @Transactional
    public void logoutWidget(
        String token,
        UUID siteId,
        String origin
    ) {
        SessionRevocationResult result = userAccountRepository.revokeSession(
            sessionTokenHasher.hash(token),
            SessionAudience.WIDGET,
            siteId,
            origin,
            clock.instant()
        );
        if (result == SessionRevocationResult.NOT_FOUND_OR_EXPIRED) {
            throw invalidSession();
        }
    }

    private ApplicationException invalidSession() {
        return new ApplicationException(
            ApiErrorCode.INVALID_SESSION,
            "Invalid or expired session"
        );
    }
}
