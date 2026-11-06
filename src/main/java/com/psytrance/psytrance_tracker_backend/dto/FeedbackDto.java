package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.Feedback;

import java.time.Instant;

/**
 * A feedback message as the admin list shows it; username is null for guests. {@code replyTo} is where an answer
 * goes (the address written in the form, else the account's), null when there is none; then the admin's answer.
 */
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
        Instant createdAt,
        String replyTo,
        String replyText,
        Instant repliedAt,
        String repliedBy,
        Accept accept
) {

    /**
     * A track or artist suggestion the inbox can add with one click (FeedbackSuggestions); null for other feedback.
     * {@code genres}: where it can go, the likeliest first; empty when nowhere yet (then it is fixed by hand).
     */
    public record Accept(String type, String artist, String title, Integer year, String youtube, java.util.List<String> genres) {
    }

    public static FeedbackDto from(Feedback feedback, String username, String replyTo, Accept accept) {
        return new FeedbackDto(feedback.getId(), feedback.getKind(), feedback.getAboutType(), feedback.getAboutRef(),
                feedback.getPageUrl(), feedback.getMessage(), feedback.getEmail(), username, feedback.getStatus(),
                feedback.getCreatedAt(), replyTo, feedback.getReplyText(), feedback.getRepliedAt(), feedback.getRepliedBy(),
                accept);
    }
}
