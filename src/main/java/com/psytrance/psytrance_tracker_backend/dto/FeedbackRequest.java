package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.Feedback;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What the feedback form sends. {@code website} is a field people never see: a bot that fills in
 * every field fills it in too, and its message is quietly dropped.
 */
public record FeedbackRequest(
        @NotNull Feedback.Kind kind,
        Feedback.About aboutType,
        @Size(max = 200) String aboutRef,
        @Size(max = 500) String pageUrl,
        @NotBlank @Size(min = 5, max = 2000) String message,
        @Email @Size(max = 255) String email,
        String website
) {
}
