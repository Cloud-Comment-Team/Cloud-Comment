package com.cloudcomment.account.api;

import com.cloudcomment.account.application.AccountDeletionConfirmationService;
import com.cloudcomment.account.application.AccountDeletionRequestService;
import com.cloudcomment.account.application.AccountDeletionRequestView;
import com.cloudcomment.account.application.PersonalDataExportService;
import com.cloudcomment.account.application.PersonalDataSnapshot;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.shared.web.security.AdminSessionCookieService;
import com.cloudcomment.shared.web.security.CurrentUser;
import com.cloudcomment.shared.web.security.PublicApi;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
class AccountController {

    private final AccountDeletionRequestService deletionRequestService;
    private final AccountDeletionConfirmationService deletionConfirmationService;
    private final PersonalDataExportService personalDataExportService;
    private final AdminSessionCookieService adminSessionCookieService;

    AccountController(
        AccountDeletionRequestService deletionRequestService,
        AccountDeletionConfirmationService deletionConfirmationService,
        PersonalDataExportService personalDataExportService,
        AdminSessionCookieService adminSessionCookieService
    ) {
        this.deletionRequestService = deletionRequestService;
        this.deletionConfirmationService = deletionConfirmationService;
        this.personalDataExportService = personalDataExportService;
        this.adminSessionCookieService = adminSessionCookieService;
    }

    @PostMapping("/deletion-requests")
    ResponseEntity<AccountDeletionRequestResponse> createDeletionRequest(
        @CurrentUser AuthenticatedUser currentUser
    ) {
        AccountDeletionRequestView request = deletionRequestService.createOrRefresh(currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountDeletionRequestResponse.from(request));
    }

    @GetMapping("/deletion-requests/current")
    AccountDeletionRequestResponse getCurrentDeletionRequest(@CurrentUser AuthenticatedUser currentUser) {
        return AccountDeletionRequestResponse.from(deletionRequestService.getCurrent(currentUser));
    }

    @GetMapping("/personal-data")
    PersonalDataResponse exportPersonalData(@CurrentUser AuthenticatedUser currentUser) {
        PersonalDataSnapshot snapshot = personalDataExportService.export(currentUser);
        return PersonalDataResponse.from(snapshot);
    }

    @PublicApi
    @PostMapping("/deletion-confirmations")
    ResponseEntity<Void> confirmDeletion(
        @Valid @RequestBody ConfirmAccountDeletionRequest request,
        HttpServletResponse response
    ) {
        deletionConfirmationService.confirm(request.token());
        adminSessionCookieService.clear(response);
        return ResponseEntity.noContent().build();
    }
}
