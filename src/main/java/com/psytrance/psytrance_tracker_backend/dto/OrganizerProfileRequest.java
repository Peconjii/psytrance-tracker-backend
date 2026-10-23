package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.util.ImageUrls;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** An organizer's page as the organizer (or an admin) writes it; links are https, blank fields clear them */
public record OrganizerProfileRequest(
        @Size(max = 500) @Pattern(regexp = ImageUrls.PATTERN, message = ImageUrls.MESSAGE) String photoUrl,
        @Size(max = 1000) String bio,
        @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE) String instagramUrl,
        @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE) String websiteUrl
) {
    // Blank (clears the field), or an https address without spaces
    static final String HTTPS = "\\s*|https://\\S+";
    static final String HTTPS_MESSAGE = "must be an https:// address";
}
