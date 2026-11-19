package com.psytrance.psytrance_tracker_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The user's Festival Buddies settings. {@code visible} is the opt-in; the rest is optional and only shown to other
 * visible people going to the same event.
 */
public record BuddySettings(
        @NotNull Boolean visible,
        @Pattern(regexp = "[A-Z]{2}", message = "country must be a two-letter code like RS") String country,
        @Size(max = 140) String note,
        @Pattern(regexp = "[A-Za-z0-9._]{1,30}", message = "Instagram name: letters, numbers, dots and underscores") String instagram) {
}
