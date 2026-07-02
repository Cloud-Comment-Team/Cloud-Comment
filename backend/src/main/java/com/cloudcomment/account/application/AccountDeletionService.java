package com.cloudcomment.account.application;

import com.cloudcomment.auth.persistence.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountDeletionService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountDeletionService(
        UserAccountRepository userAccountRepository,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        Instant now = clock.instant();
        String anonymizedEmail = "deleted-" + userId + "@deleted.invalid";
        String unusablePasswordHash = passwordEncoder.encode("deleted-" + userId);
        userAccountRepository.markAccountDeleted(userId, anonymizedEmail, unusablePasswordHash, now);
        userAccountRepository.revokeAllSessions(userId, now);
    }
}
