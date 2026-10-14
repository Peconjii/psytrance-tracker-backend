package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.User;

/** What the API shows about a user. The password hash never leaves the backend. */
public record UserResponse(Long id, String username, String email, boolean eventReminders) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.isEventReminders());
    }
}
