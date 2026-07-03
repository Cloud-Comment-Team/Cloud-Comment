package com.cloudcomment.account.application;

import com.cloudcomment.account.persistence.PersonalDataRepository;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.privacy.application.PrivacyAuditService;
import com.cloudcomment.privacy.domain.PrivacyEventType;
import com.cloudcomment.shared.error.ApiErrorCode;
import com.cloudcomment.shared.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersonalDataExportService {

    private final PersonalDataRepository personalDataRepository;
    private final PrivacyAuditService privacyAuditService;
    private final Clock clock;

    @Transactional
    public PersonalDataSnapshot export(AuthenticatedUser currentUser) {
        Instant now = clock.instant();
        PersonalDataSnapshot snapshot = personalDataRepository.findByUserId(currentUser.id(), now)
            .orElseThrow(() -> new ApplicationException(ApiErrorCode.NOT_FOUND, "Resource not found"));
        privacyAuditService.record(currentUser.id(), PrivacyEventType.PERSONAL_DATA_EXPORTED, Map.of(
            "exportedAt", snapshot.exportedAt().toString()
        ));
        return snapshot;
    }
}
