package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.util.ImageUrls;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An event added on our site. Times are on the venue's clock ({@code timeZone}, e.g. "Europe/Lisbon"); the line-up is
 * free text like on Goabase (stage headers and "22:00 Artist" lines are understood). Links are https; blank fields
 * are left out.
 */
public record OurEventRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 40) String eventType,
        @NotNull LocalDateTime startsAt,
        LocalDateTime endsAt,
        @NotBlank @Size(max = 60) String timeZone,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 100) String town,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
        @Size(max = 10000) String lineUp,
        @Size(max = 2000) String locationText,
        @Size(max = 500) String entryFee,
        @Size(max = 5000) String info,
        @Size(max = 200) String organizerName,
        @Size(max = 500) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE) String organizerUrl,
        @Size(max = 500) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE) String ticketUrl,
        @Size(max = 500) @Pattern(regexp = ImageUrls.PATTERN, message = ImageUrls.MESSAGE) String imageUrl,
        @Size(max = 4) List<@NotBlank String> genres
) {
    // Blank, or an https address without spaces
    static final String HTTPS = "\\s*|https://\\S+";
    static final String HTTPS_MESSAGE = "must be an https:// address";
}
