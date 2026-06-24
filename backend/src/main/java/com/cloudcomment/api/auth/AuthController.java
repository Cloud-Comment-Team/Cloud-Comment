package com.cloudcomment.api.auth;

import com.cloudcomment.api.error.ApiErrorCode;
import com.cloudcomment.api.error.ApiException;
import com.cloudcomment.service.LoginResult;
import com.cloudcomment.service.LoginService;
import com.cloudcomment.service.LogoutService;
import com.cloudcomment.service.RegisteredUser;
import com.cloudcomment.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final LogoutService logoutService;

    AuthController(
        RegistrationService registrationService,
        LoginService loginService,
        LogoutService logoutService
    ) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.logoutService = logoutService;
    }

    @PostMapping("/register")
    ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisteredUser user = registrationService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterUserResponse.from(user));
    }

    @PostMapping("/login")
    LoginUserResponse login(@Valid @RequestBody LoginUserRequest request) {
        LoginResult result = loginService.login(request.email(), request.password());
        return LoginUserResponse.from(result);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        logoutService.logout(extractBearerToken(authorization));
        return ResponseEntity.noContent().build();
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null) {
            throw invalidSession();
        }

        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw invalidSession();
        }

        String token = authorization.substring(prefix.length()).trim();
        if (token.isEmpty()) {
            throw invalidSession();
        }
        return token;
    }

    private ApiException invalidSession() {
        return new ApiException(
            ApiErrorCode.INVALID_SESSION,
            HttpStatus.UNAUTHORIZED,
            "Invalid or expired session"
        );
    }
}
