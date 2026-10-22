package com.psytrance.psytrance_tracker_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may use the admin side. Two roles above a user:
 * <ul>
 *   <li>ADMIN: everything. The admins in configuration ({@code ADMIN_USERNAMES=alice,bob}) always are, so the site
 *       never ends up without one; more are given in the admin panel (table {@code user_roles}).</li>
 *   <li>EDITOR: fixes the content (events, their genres and timetables, artist genres, added events, reviews), but
 *       doesn't see the inbox or the accounts, decide requests, undo in the history or give roles.</li>
 * </ul>
 * Names are compared exactly: usernames are case-sensitive, so "Admin" is a different account from "admin".
 */
@Component
public class Admins {

    public static final String ADMIN = "ADMIN";
    public static final String EDITOR = "EDITOR";

    private final Set<String> usernames;
    private final JdbcClient jdbc;

    public Admins(@Value("${app.admin-usernames:}") String usernames, JdbcClient jdbc) {
        this.usernames = Arrays.stream(usernames.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.jdbc = jdbc;
    }

    /** An admin from ADMIN_USERNAMES: the panel can't take the role away */
    public boolean isConfigured(String username) {
        return username != null && usernames.contains(username);
    }

    /** The admins from ADMIN_USERNAMES */
    public Set<String> configured() {
        return usernames;
    }

    /** ADMIN, EDITOR, or empty for everyone else */
    public Optional<String> roleOf(String username) {
        if (username == null) {
            return Optional.empty();
        }
        if (isConfigured(username)) {
            return Optional.of(ADMIN);
        }
        return jdbc.sql("select r.role from user_roles r join app_user u on u.id = r.user_id where u.username = :username")
                .param("username", username).query(String.class).optional();
    }

    public boolean isAdmin(String username) {
        return roleOf(username).filter(ADMIN::equals).isPresent();
    }

    /** An editor or an admin: who may fix the content */
    public boolean isEditor(String username) {
        return roleOf(username).isPresent();
    }

    /** The admin's username, or 403 for everyone else (guests never reach admin endpoints, see SecurityConfig) */
    public String requireAdmin(Authentication authentication) {
        String username = username(authentication);
        if (!isAdmin(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can do this");
        }
        return username;
    }

    /** The editor's or admin's username, or 403 for everyone else */
    public String requireEditor(Authentication authentication) {
        String username = username(authentication);
        if (!isEditor(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only editors and admins can do this");
        }
        return username;
    }

    private static String username(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken
                ? null
                : authentication.getName();
    }
}
