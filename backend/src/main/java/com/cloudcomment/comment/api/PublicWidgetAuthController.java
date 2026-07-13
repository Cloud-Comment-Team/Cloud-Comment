package com.cloudcomment.comment.api;

import com.cloudcomment.auth.api.LoginUserRequest;
import com.cloudcomment.auth.api.LoginUserResponse;
import com.cloudcomment.auth.api.RegisterUserRequest;
import com.cloudcomment.auth.api.RegisterUserResponse;
import com.cloudcomment.auth.api.UserProfileResponse;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.LoginService;
import com.cloudcomment.auth.application.LogoutService;
import com.cloudcomment.auth.application.RegisteredUser;
import com.cloudcomment.auth.application.RegistrationService;
import com.cloudcomment.auth.domain.SessionAudience;
import com.cloudcomment.comment.application.DomainPolicyService;
import com.cloudcomment.privacy.application.RegistrationConsent;
import com.cloudcomment.privacy.domain.ConsentSource;
import com.cloudcomment.shared.web.security.BearerTokenResolver;
import com.cloudcomment.shared.web.security.CurrentUser;
import com.cloudcomment.shared.web.security.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final LogoutService logoutService;
    private final BearerTokenResolver bearerTokenResolver;
    private final WidgetRequestOriginResolver requestOriginResolver;

    @PublicApi
    @PostMapping("/register")
    ResponseEntity<RegisterUserResponse> register(
        @PathVariable UUID siteId,
        HttpServletRequest servletRequest,
        @Valid @RequestBody RegisterUserRequest request
    ) {
        String origin = requestOriginResolver.resolve(servletRequest);
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
        HttpServletRequest servletRequest,
        @Valid @RequestBody LoginUserRequest request
    ) {
        String origin = requestOriginResolver.resolve(servletRequest);
        domainPolicyService.validate(siteId, origin);
        return LoginUserResponse.from(loginService.login(
            request.email(),
            request.password(),
            SessionAudience.WIDGET
        ));
    }

    @GetMapping("/me")
    UserProfileResponse me(
        @PathVariable UUID siteId,
        HttpServletRequest servletRequest,
        @CurrentUser AuthenticatedUser currentUser
    ) {
        domainPolicyService.validate(siteId, requestOriginResolver.resolve(servletRequest));
        return UserProfileResponse.from(currentUser);
    }

    @PublicApi
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
        @PathVariable UUID siteId,
        HttpServletRequest servletRequest,
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        domainPolicyService.validate(siteId, requestOriginResolver.resolve(servletRequest));
        logoutService.logout(bearerTokenResolver.resolve(authorization), SessionAudience.WIDGET);
        return ResponseEntity.noContent().build();
    }
}
