package com.cloudcomment.comment.api;

import com.cloudcomment.account.api.AccountDeletionRequestResponse;
import com.cloudcomment.account.api.PersonalDataResponse;
import com.cloudcomment.account.application.AccountDeletionRequestService;
import com.cloudcomment.account.application.AccountDeletionRequestView;
import com.cloudcomment.account.application.PersonalDataExportService;
import com.cloudcomment.account.application.PersonalDataSnapshot;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.shared.web.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/sites/{siteId}/account")
@RequiredArgsConstructor
class PublicWidgetAccountController {

    private final DomainPolicyService domainPolicyService;
    private final AccountDeletionRequestService deletionRequestService;
    private final PersonalDataExportService personalDataExportService;
    private final WidgetRequestOriginResolver requestOriginResolver;

    @GetMapping("/personal-data")
    PersonalDataResponse exportPersonalData(
        @PathVariable UUID siteId,
        HttpServletRequest servletRequest,
        @CurrentUser AuthenticatedUser currentUser
    ) {
        domainPolicyService.validate(siteId, requestOriginResolver.resolve(servletRequest));
        PersonalDataSnapshot snapshot = personalDataExportService.export(currentUser);
        return PersonalDataResponse.from(snapshot);
    }

    @PostMapping("/deletion-requests")
    ResponseEntity<AccountDeletionRequestResponse> createDeletionRequest(
        @PathVariable UUID siteId,
        HttpServletRequest servletRequest,
        @CurrentUser AuthenticatedUser currentUser
    ) {
        domainPolicyService.validate(siteId, requestOriginResolver.resolve(servletRequest));
        AccountDeletionRequestView request = deletionRequestService.createOrRefresh(currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountDeletionRequestResponse.from(request));
    }
}
