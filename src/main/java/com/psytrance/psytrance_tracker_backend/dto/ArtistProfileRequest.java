package com.psytrance.psytrance_tracker_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An artist's profile as an admin writes it. Links must be https addresses, so a profile can never carry a
 * "javascript:" link or send visitors over plain http; blank fields clear the field.
 */
public record ArtistProfileRequest(
        @Size(max = 500) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE) String photoUrl,
        @Size(max = 1000) String bio,
        @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE) String soundcloudUrl,
        @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE) String bandcampUrl,
        @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE) String instagramUrl,
        @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE) String websiteUrl,
        @Size(max = 255) @Email String bookingEmail
) {
    // Blank (clears the field), or an https address without spaces
    static final String HTTPS = "\\s*|https://\\S+";
    static final String HTTPS_MESSAGE = "must be an https:// address";
}
