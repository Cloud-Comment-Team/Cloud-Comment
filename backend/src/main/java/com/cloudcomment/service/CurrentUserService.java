package com.cloudcomment.service;

import com.cloudcomment.api.error.ApiErrorCode;
import com.cloudcomment.api.error.ApiException;
import com.cloudcomment.persistence.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

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
    public AuthenticatedUser getCurrentUser(String token) {
        return userAccountRepository.findUserByActiveSessionTokenHash(
                sessionTokenHasher.hash(token),
                clock.instant()
            )
            .orElseThrow(this::invalidSession);
    }

    private ApiException invalidSession() {
        return new ApiException(
            ApiErrorCode.INVALID_SESSION,
            HttpStatus.UNAUTHORIZED,
            "Invalid or expired session"
        );
    }
}
