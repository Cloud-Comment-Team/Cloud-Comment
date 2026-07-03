package com.cloudcomment.comment.api;

import com.cloudcomment.auth.api.LoginUserRequest;
import com.cloudcomment.auth.api.LoginUserResponse;
import com.cloudcomment.auth.api.RegisterUserRequest;
import com.cloudcomment.auth.api.RegisterUserResponse;
import com.cloudcomment.auth.application.LoginService;
import com.cloudcomment.auth.application.RegisteredUser;
import com.cloudcomment.auth.application.RegistrationService;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.privacy.application.RegistrationConsent;
import com.cloudcomment.privacy.domain.ConsentSource;
import com.cloudcomment.shared.web.security.PublicApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/sites/{siteId}/auth")
@RequiredArgsConstructor
class PublicWidgetAuthController {

    private final DomainPolicyService domainPolicyService;
    private final RegistrationService registrationService;
    private final LoginService loginService;

    @PublicApi
    @PostMapping("/register")
    ResponseEntity<RegisterUserResponse> register(
        @PathVariable UUID siteId,
        @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
        @Valid @RequestBody RegisterUserRequest request
    ) {
        domainPolicyService.validate(siteId, origin);
        RegisteredUser user = registrationService.register(
            request.email(),
            request.password(),
            RegistrationConsent.from(request),
            ConsentSource.WIDGET
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterUserResponse.from(user));
    }

    @PublicApi
    @PostMapping("/login")
    LoginUserResponse login(
        @PathVariable UUID siteId,
        @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
        @Valid @RequestBody LoginUserRequest request
    ) {
        domainPolicyService.validate(siteId, origin);
        return LoginUserResponse.from(loginService.login(request.email(), request.password()));
    }
}
