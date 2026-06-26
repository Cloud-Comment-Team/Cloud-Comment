package com.cloudcomment.auth.api;

import com.cloudcomment.shared.web.security.BearerTokenResolver;
import com.cloudcomment.shared.web.security.CurrentUser;
import com.cloudcomment.shared.web.security.PublicApi;
import com.cloudcomment.auth.application.AuthenticatedUser;
import com.cloudcomment.auth.application.LoginResult;
import com.cloudcomment.auth.application.LoginService;
import com.cloudcomment.auth.application.LogoutService;
import com.cloudcomment.auth.application.RegisteredUser;
import com.cloudcomment.auth.application.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final BearerTokenResolver bearerTokenResolver;

    AuthController(
        RegistrationService registrationService,
        LoginService loginService,
        LogoutService logoutService,
        BearerTokenResolver bearerTokenResolver
    ) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.logoutService = logoutService;
        this.bearerTokenResolver = bearerTokenResolver;
    }

    @PublicApi
    @PostMapping("/register")
    ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisteredUser user = registrationService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterUserResponse.from(user));
    }

    @PublicApi
    @PostMapping("/login")
    LoginUserResponse login(@Valid @RequestBody LoginUserRequest request) {
        LoginResult result = loginService.login(request.email(), request.password());
        return LoginUserResponse.from(result);
    }

    @PublicApi
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        logoutService.logout(bearerTokenResolver.resolve(authorization));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserProfileResponse me(@CurrentUser AuthenticatedUser currentUser) {
        return UserProfileResponse.from(currentUser);
    }
}
