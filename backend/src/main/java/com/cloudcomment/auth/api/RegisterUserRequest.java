package com.cloudcomment.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
    @NotBlank
    @Email
    @Size(max = 320)
    String email,

    @NotBlank
    @Size(min = 8, max = 72)
    String password
) {
}
