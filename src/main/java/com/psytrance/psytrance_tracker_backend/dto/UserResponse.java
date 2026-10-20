package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.User;

/**
 * What the API shows about a user. The password hash never leaves the backend. {@code admin}
 * tells the frontend to show the feedback admin page (the backend checks it again on every request).
 */
public record UserResponse(Long id, String username, String email, boolean eventReminders, boolean admin) {

    public static UserResponse from(User user) {
        return from(user, false);
    }

    public static UserResponse from(User user, boolean admin) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.isEventReminders(), admin);
    }
}
