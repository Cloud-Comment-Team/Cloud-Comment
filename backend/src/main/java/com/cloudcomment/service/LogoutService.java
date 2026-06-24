package com.cloudcomment.service;

import com.cloudcomment.api.error.ApiErrorCode;
import com.cloudcomment.api.error.ApiException;
import com.cloudcomment.persistence.SessionRevocationResult;
import com.cloudcomment.persistence.UserAccountRepository;
import org.springframework.http.HttpStatus;
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
    public void logout(String token) {
        SessionRevocationResult result = userAccountRepository.revokeSession(
            sessionTokenHasher.hash(token),
            clock.instant()
        );

        if (result == SessionRevocationResult.NOT_FOUND_OR_EXPIRED) {
            throw invalidSession();
        }
    }

    private ApiException invalidSession() {
        return new ApiException(
            ApiErrorCode.INVALID_SESSION,
            HttpStatus.UNAUTHORIZED,
            "Invalid or expired session"
        );
    }
}
