package com.cloudcomment.account.application;

import com.cloudcomment.account.domain.AccountDeletionRequest;
import com.cloudcomment.account.persistence.AccountDeletionRequestRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.SessionTokenHasher;
import com.cloudcomment.auth.persistence.UserAccountRepository;
import com.cloudcomment.privacy.application.PrivacyAuditService;
import com.cloudcomment.privacy.domain.PrivacyEventType;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import com.cloudcomment.shared.mail.MailMessage;
import com.cloudcomment.shared.mail.MailProperties;
import com.cloudcomment.shared.mail.MailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountDeletionRequestService {

    private static final Duration CONFIRMATION_TTL = Duration.ofHours(24);
    private static final int TOKEN_BYTES = 32;

    private final AccountDeletionRequestRepository deletionRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final SessionTokenHasher sessionTokenHasher;
    private final MailSender mailSender;
    private final MailProperties mailProperties;
    private final PrivacyAuditService privacyAuditService;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public AccountDeletionRequestService(
        AccountDeletionRequestRepository deletionRequestRepository,
        UserAccountRepository userAccountRepository,
        SessionTokenHasher sessionTokenHasher,
        MailSender mailSender,
        MailProperties mailProperties,
        PrivacyAuditService privacyAuditService,
        Clock clock
    ) {
        this.deletionRequestRepository = deletionRequestRepository;
        this.userAccountRepository = userAccountRepository;
        this.sessionTokenHasher = sessionTokenHasher;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.privacyAuditService = privacyAuditService;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public AccountDeletionRequestView createOrRefresh(AuthenticatedUser currentUser) {
        assertActiveAccount(currentUser.id());

        Instant now = clock.instant();
        String token = generateToken();
        String tokenHash = sessionTokenHasher.hash(token);
        Instant expiresAt = now.plus(CONFIRMATION_TTL);

        AccountDeletionRequest request = deletionRequestRepository.findActiveByUserId(currentUser.id(), now)
            .map(existing -> deletionRequestRepository.rotateToken(existing.id(), tokenHash, expiresAt, now))
            .orElseGet(() -> {
                deletionRequestRepository.cancelPendingForUser(currentUser.id(), now);
                return deletionRequestRepository.create(currentUser.id(), tokenHash, expiresAt);
            });

        sendConfirmationEmail(currentUser.email(), token, expiresAt);
        privacyAuditService.record(currentUser.id(), PrivacyEventType.ACCOUNT_DELETION_REQUESTED, Map.of(
            "requestId", request.id().toString(),
            "expiresAt", expiresAt.toString()
        ));
        return AccountDeletionRequestView.from(request);
    }

    @Transactional(readOnly = true)
    public AccountDeletionRequestView getCurrent(AuthenticatedUser currentUser) {
        assertActiveAccount(currentUser.id());
        return deletionRequestRepository.findActiveByUserId(currentUser.id(), clock.instant())
            .map(AccountDeletionRequestView::from)
            .orElseThrow(this::notFound);
    }

    private void assertActiveAccount(UUID userId) {
        if (!userAccountRepository.isActiveAccount(userId)) {
            throw notFound();
        }
    }

    private void sendConfirmationEmail(String email, String token, Instant expiresAt) {
        String confirmationUrl = mailProperties.confirmationBaseUrl() + "?token=" + token;
        mailSender.send(new MailMessage(
            email,
            "Подтверждение удаления аккаунта CloudComment",
            """
                Вы запросили удаление аккаунта CloudComment.

                Чтобы подтвердить удаление, откройте ссылку:
                %s

                Если вы не хотите переходить по ссылке, откройте страницу подтверждения:
                %s

                И вставьте одноразовый код:
                %s

                Код действует до %s UTC.
                """.formatted(confirmationUrl, mailProperties.confirmationBaseUrl(), token, expiresAt)
        ));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApplicationException notFound() {
        return new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found");
    }
}
