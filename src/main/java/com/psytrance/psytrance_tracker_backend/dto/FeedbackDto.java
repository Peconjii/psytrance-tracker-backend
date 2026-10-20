package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.Feedback;

import java.time.Instant;

/** A feedback message as the admin list shows it; username is null for guests. */
public record FeedbackDto(
        Long id,
        Feedback.Kind kind,
        Feedback.About aboutType,
        String aboutRef,
        String pageUrl,
        String message,
        String email,
        String username,
        Feedback.Status status,
        Instant createdAt
) {

    public static FeedbackDto from(Feedback feedback, String username) {
        return new FeedbackDto(feedback.getId(), feedback.getKind(), feedback.getAboutType(), feedback.getAboutRef(),
                feedback.getPageUrl(), feedback.getMessage(), feedback.getEmail(), username, feedback.getStatus(),
                feedback.getCreatedAt());
    }
}
