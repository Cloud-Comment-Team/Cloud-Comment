package com.cloudcomment.auth.api;

import com.cloudcomment.shared.web.security.AdminCsrfTokenService;
import com.cloudcomment.shared.web.security.AdminSessionCookieService;
import com.cloudcomment.shared.web.security.CurrentUser;
import com.cloudcomment.shared.web.security.PublicApi;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.LoginResult;
import com.cloudcomment.auth.application.LoginService;
import com.cloudcomment.auth.application.LogoutService;
import com.cloudcomment.auth.application.RegisteredUser;
import com.cloudcomment.auth.application.RegistrationService;
import com.cloudcomment.auth.domain.SessionAudience;
import com.cloudcomment.privacy.application.RegistrationConsent;
import com.cloudcomment.privacy.domain.ConsentSource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final AdminSessionCookieService adminSessionCookieService;
    private final AdminCsrfTokenService csrfTokenService;

    AuthController(
        RegistrationService registrationService,
        LoginService loginService,
        LogoutService logoutService,
        AdminSessionCookieService adminSessionCookieService,
        AdminCsrfTokenService csrfTokenService
    ) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.logoutService = logoutService;
        this.adminSessionCookieService = adminSessionCookieService;
        this.csrfTokenService = csrfTokenService;
    }

    @PublicApi
    @PostMapping("/register")
    ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisteredUser user = registrationService.register(
            request.email(),
            request.password(),
            request.displayName(),
            RegistrationConsent.from(request),
            ConsentSource.ADMIN
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterUserResponse.from(user));
    }

    @PublicApi
    @PostMapping("/login")
    AdminLoginResponse login(
        @Valid @RequestBody LoginUserRequest request,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        LoginResult result = loginService.loginReplacing(
            request.email(),
            request.password(),
            SessionAudience.ADMIN,
            adminSessionCookieService.resolveOptional(servletRequest).orElse(null)
        );
        adminSessionCookieService.write(servletResponse, result.token(), result.expiresAt());
        return AdminLoginResponse.from(result);
    }

    @PublicApi
    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            adminSessionCookieService.resolveOptional(request)
                .ifPresent(token -> logoutService.logoutIfPresent(token, SessionAudience.ADMIN));
        } finally {
            adminSessionCookieService.clear(response);
        }
        return ResponseEntity.noContent().build();
    }

    @PublicApi
    @GetMapping("/csrf")
    ResponseEntity<CsrfTokenResponse> csrf(HttpServletResponse response) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(CsrfTokenResponse.from(csrfTokenService.issue(response)));
    }

    @GetMapping("/me")
    UserProfileResponse me(@CurrentUser AuthenticatedUser currentUser) {
        return UserProfileResponse.from(currentUser);
    }
}
