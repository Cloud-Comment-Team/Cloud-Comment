package com.cloudcomment.account.api;

import jakarta.validation.constraints.NotBlank;

public record ConfirmAccountDeletionRequest(
    @NotBlank String token
) {
}
