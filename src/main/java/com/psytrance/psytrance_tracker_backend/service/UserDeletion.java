package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;

/**
 * Admin → Users → Delete: an admin removes an account (a test account, spam, or someone who asked to be removed).
 * It goes the same way as "Delete my account" (AccountDeletion): what was the person's goes, the pages they owned stay
 * without an owner, feedback stays without a name. One difference: the events they added stay on the site and move to
 * the admin who deletes the account. Nobody deletes themselves here or an admin from ADMIN_USERNAMES. The deletion is
 * in the history (no undo: the account is gone).
 */
@Service
public class UserDeletion {

    private final JdbcClient jdbc;
    private final Admins admins;
    private final AccountDeletion accountDeletion;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public UserDeletion(JdbcClient jdbc, Admins admins, AccountDeletion accountDeletion,
                        AdminChangeRepository changeRepository, Clock clock) {
        this.jdbc = jdbc;
        this.admins = admins;
        this.accountDeletion = accountDeletion;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    @Transactional
    public void delete(String username, String adminUsername) {
        if (username.equals(adminUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You can't delete your own account here");
        }
        if (admins.isConfigured(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This admin is set on the server (ADMIN_USERNAMES): take it out there first");
        }
        long userId = idOf(username);
        long adminId = idOf(adminUsername);
        String email = jdbc.sql("select email from app_user where id = :id").param("id", userId)
                .query(String.class).optional().orElse("");

        int events = jdbc.sql("update our_events set submitted_by = :admin where submitted_by = :id")
                .param("admin", adminId).param("id", userId).update();
        accountDeletion.erase(userId);

        changeRepository.save(new AdminChange(adminUsername, AdminChange.USER_DELETED, username,
                email, events > 0 ? events + " added event(s) moved to " + adminUsername : "", clock.instant()));
    }

    private long idOf(String username) {
        return jdbc.sql("select id from app_user where username = :username").param("username", username)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account " + username));
    }
}
