package com.cloudcomment.auth.application;

import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.privacy.application.ConsentService;
import com.cloudcomment.privacy.application.RegistrationConsent;
import com.cloudcomment.privacy.domain.ConsentSource;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
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
    private final ConsentService consentService;

    public RegistrationService(
        UserAccountRepository userAccountRepository,
        PasswordEncoder passwordEncoder,
        ConsentService consentService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.consentService = consentService;
    }

    @Transactional
    public RegisteredUser register(
        String email,
        String password,
        RegistrationConsent consent,
        ConsentSource source
    ) {
        return register(email, password, null, consent, source);
    }

    @Transactional
    public RegisteredUser register(
        String email,
        String password,
        String displayName,
        RegistrationConsent consent,
        ConsentSource source
    ) {
        consentService.validate(consent);

        String normalizedEmail = normalizeEmail(email);
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw emailAlreadyUsed();
        }

        String passwordHash = passwordEncoder.encode(password);
        try {
            RegisteredUser user = userAccountRepository.create(
                normalizedEmail,
                passwordHash,
                normalizeDisplayName(displayName),
                DEFAULT_ROLES
            );
            consentService.recordConsent(user.id(), consent, source);
            return user;
        } catch (DuplicateKeyException exception) {
            throw emailAlreadyUsed();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String normalized = displayName.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private ApplicationException emailAlreadyUsed() {
        return new ApplicationException(ApiErrorCode.EMAIL_ALREADY_USED, "Email is already used");
    }
}
