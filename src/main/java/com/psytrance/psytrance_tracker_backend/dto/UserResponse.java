package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.User;

/**
 * What the API shows about a user. The password hash never leaves the backend. {@code admin} and {@code editor}
 * tell the frontend which admin pages to show (the backend checks them again on every request); an admin is an
 * editor too. {@code emailVerified}: the user opened the emailed link, so they may add events.
 * {@code weeklyDigest}: the Monday email with the next gigs of who they follow.
 */
public record UserResponse(Long id, String username, String email, boolean eventReminders, boolean weeklyDigest,
                           boolean admin, boolean editor, boolean emailVerified) {

    public static UserResponse from(User user) {
        return from(user, null);
    }

    /** role: ADMIN, EDITOR or null (see Admins) */
    public static UserResponse from(User user, String role) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.isEventReminders(),
                user.isWeeklyDigest(), "ADMIN".equals(role), role != null, user.isEmailVerified());
    }
}
