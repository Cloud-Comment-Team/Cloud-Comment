package com.cloudcomment.api.auth;

import com.cloudcomment.service.LoginResult;
import com.cloudcomment.service.LoginService;
import com.cloudcomment.service.RegisteredUser;
import com.cloudcomment.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;

    AuthController(RegistrationService registrationService, LoginService loginService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
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
}
