package com.psytrance.psytrance_tracker_backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Admin → Users: how many accounts there are and the newest ones with what they use. {@code createdAt} is null for
 * accounts made before the site kept the date (21.10.2026 and earlier).
 */
public record AdminUsersDto(long total, long verified, long newThisWeek, List<User> users) {

    /** role: ADMIN, EDITOR or null; roleOnServer: an admin from ADMIN_USERNAMES, which the panel can't change */
    public record User(long id, String username, String email, Instant createdAt, boolean emailVerified, String role,
                       boolean roleOnServer,
                       long follows, long labelFollows, long favorites, long going, long reviews, long eventsAdded) {
    }
}
