package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Admin → Users: an admin makes an account an admin or an editor, or takes the role away (see Admins for what each
 * may do). The admins from ADMIN_USERNAMES can't be changed here, and nobody changes their own role, so the site always
 * keeps an admin. Every change is in the history.
 */
@Service
public class UserRoles {

    public static final List<String> ROLES = List.of(Admins.ADMIN, Admins.EDITOR);

    private final JdbcClient jdbc;
    private final Admins admins;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public UserRoles(JdbcClient jdbc, Admins admins, AdminChangeRepository changeRepository, Clock clock) {
        this.jdbc = jdbc;
        this.admins = admins;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    /** role: ADMIN, EDITOR, or null to make them a user again. Returns the role they have now (null for none). */
    @Transactional
    public String set(String username, String role, String adminUsername) {
        if (role != null && !ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The role is ADMIN, EDITOR or none");
        }
        Long userId = jdbc.sql("select id from app_user where username = :username").param("username", username)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account " + username));
        if (username.equals(adminUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You can't change your own role: ask another admin");
        }
        if (admins.isConfigured(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This admin is set on the server (ADMIN_USERNAMES): change it there");
        }
        String before = admins.roleOf(username).orElse(null);
        if (Objects.equals(before, role)) {
            return role;
        }
        jdbc.sql("delete from user_roles where user_id = :user").param("user", userId).update();
        if (role != null) {
            jdbc.sql("""
                            insert into user_roles (user_id, role, granted_by, granted_at) values (:user, :role, :admin, :now)
                            """)
                    .param("user", userId).param("role", role).param("admin", adminUsername)
                    .param("now", Timestamp.from(clock.instant())).update();
        }
        changeRepository.save(new AdminChange(adminUsername, AdminChange.USER_ROLE, username,
                before == null ? "" : before, role == null ? "" : role, clock.instant()));
        return role;
    }
}
