package com.psytrance.psytrance_tracker_backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Admin → Users: how many accounts there are and the newest ones with what they use. {@code createdAt} is null for
 * accounts made before the site kept the date (21.10.2026 and earlier).
 */
public record AdminUsersDto(long total, long verified, long newThisWeek, List<User> users) {

    /**
     * role: ADMIN, EDITOR or null; roleOnServer: an admin from ADMIN_USERNAMES, which the panel can't change;
     * pages: the artist, organizer, label and festival pages the account owns
     */
    public record User(long id, String username, String email, Instant createdAt, boolean emailVerified, String role,
                       boolean roleOnServer,
                       long follows, long labelFollows, long favorites, long going, long reviews, long eventsAdded,
                       List<Page> pages) {

        public User withPages(List<Page> pages) {
            return new User(id, username, email, createdAt, emailVerified, role, roleOnServer,
                    follows, labelFollows, favorites, going, reviews, eventsAdded, pages);
        }
    }

    /** kind: ARTIST, ORGANIZER, LABEL or FESTIVAL; key: the slug, the label's name or the festival's id (see PageTrust) */
    public record Page(String kind, String key, String name, boolean trusted) {
    }
}
