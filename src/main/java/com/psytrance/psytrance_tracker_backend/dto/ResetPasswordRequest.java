package com.psytrance.psytrance_tracker_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Same password rules as {@link RegisterRequest}. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
