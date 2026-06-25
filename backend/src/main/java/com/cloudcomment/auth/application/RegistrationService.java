package com.cloudcomment.auth.application;

import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class RegistrationService {

    private static final Set<String> DEFAULT_ROLES = Set.of("COMMENTER");

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisteredUser register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw emailAlreadyUsed();
        }

        String passwordHash = passwordEncoder.encode(password);
        try {
            return userAccountRepository.create(normalizedEmail, passwordHash, DEFAULT_ROLES);
        } catch (DuplicateKeyException exception) {
            throw emailAlreadyUsed();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApplicationException emailAlreadyUsed() {
        return new ApplicationException(ApiErrorCode.EMAIL_ALREADY_USED, "Email is already used");
    }
}
