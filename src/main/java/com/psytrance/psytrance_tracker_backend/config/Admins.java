package com.psytrance.psytrance_tracker_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may read and handle feedback. There are only two roles, user and admin, and admins are few,
 * so they are listed in configuration ({@code ADMIN_USERNAMES=alice,bob}) instead of a column in the database.
 */
@Component
public class Admins {

    private final Set<String> usernames;

    public Admins(@Value("${app.admin-usernames:}") String usernames) {
        this.usernames = Arrays.stream(usernames.split(","))
                .map(name -> name.strip().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String username) {
        return username != null && usernames.contains(username.toLowerCase(Locale.ROOT));
    }
}
