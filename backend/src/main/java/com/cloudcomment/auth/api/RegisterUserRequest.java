package com.cloudcomment.auth.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
    @NotBlank
    @Email
    @Size(max = 320)
    String email,

    @NotBlank
    @Size(min = 8, max = 72)
    String password,

    @NotNull
    @AssertTrue(message = "Privacy policy consent is required")
    Boolean acceptedPrivacyPolicy,

    @NotNull
    @AssertTrue(message = "Terms consent is required")
    Boolean acceptedTerms,

    @NotBlank
    @Size(max = 64)
    String privacyPolicyVersion,

    @NotBlank
    @Size(max = 64)
    String termsVersion
) {
}
