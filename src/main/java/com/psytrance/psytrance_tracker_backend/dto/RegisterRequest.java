package com.psytrance.psytrance_tracker_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code website}: the hidden field people never see; only bots fill it in (see FormLimits) */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        String website
) {

    public RegisterRequest(String username, String email, String password) {
        this(username, email, password, null);
    }
}
