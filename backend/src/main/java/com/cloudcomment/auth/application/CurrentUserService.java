package com.cloudcomment.auth.application;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.auth.domain.SessionAudience;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;
    private final SessionTokenHasher sessionTokenHasher;
    private final Clock clock;

    public CurrentUserService(
        UserAccountRepository userAccountRepository,
        SessionTokenHasher sessionTokenHasher,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.sessionTokenHasher = sessionTokenHasher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser getCurrentUser(String token, SessionAudience audience) {
        if (audience == SessionAudience.LEGACY || audience == SessionAudience.WIDGET) {
            throw invalidSession();
        }
        return userAccountRepository.findUserByActiveSessionTokenHash(
                sessionTokenHasher.hash(token),
                audience,
                null,
                null,
                clock.instant()
            )
            .orElseThrow(this::invalidSession);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser getWidgetCurrentUser(
        String token,
        UUID siteId,
        String origin
    ) {
        return userAccountRepository.findUserByActiveSessionTokenHash(
                sessionTokenHasher.hash(token),
                SessionAudience.WIDGET,
                siteId,
                origin,
                clock.instant()
            )
            .orElseThrow(this::invalidSession);
    }

    private ApplicationException invalidSession() {
        return new ApplicationException(
            ApiErrorCode.INVALID_SESSION,
            "Invalid or expired session"
        );
    }
}
