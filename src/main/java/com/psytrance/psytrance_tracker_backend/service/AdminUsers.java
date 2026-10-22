package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.AdminUsersDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Admin → Users: the accounts, newest first, with how much each uses the site. Read straight with SQL counts. */
@Service
public class AdminUsers {

    /** The newest accounts shown; the totals count all of them */
    static final int SHOWN = 300;

    private final JdbcClient jdbc;
    private final Admins admins;
    private final Clock clock;

    public AdminUsers(JdbcClient jdbc, Admins admins, Clock clock) {
        this.jdbc = jdbc;
        this.admins = admins;
        this.clock = clock;
    }

    /** team: only the admins and editors */
    public AdminUsersDto list(String search, boolean team) {
        String pattern = search == null || search.isBlank() ? null : "%" + search.strip().toLowerCase() + "%";
        // "" keeps the list from being empty, which SQL's "in ()" doesn't allow
        List<String> configured = new ArrayList<>(admins.configured());
        configured.add("");
        // Ids come from a sequence, so they go up with every new account, also for the ones without a date
        List<AdminUsersDto.User> users = jdbc.sql("""
                        select u.id, u.username, u.email, u.created_at, u.email_verified, r.role,
                               (select count(*) from artist_follows f where f.user_id = u.id) as follows,
                               (select count(*) from label_follows l where l.user_id = u.id) as label_follows,
                               (select count(*) from favorites f where f.user_id = u.id) as favorites,
                               (select count(*) from attendances a where a.user_id = u.id) as going,
                               (select count(*) from reviews r where r.user_id = u.id) as reviews,
                               (select count(*) from our_events o where o.submitted_by = u.id) as events_added
                        from app_user u left join user_roles r on r.user_id = u.id
                        where (cast(:pattern as varchar) is null
                           or lower(u.username) like :pattern or lower(coalesce(u.email, '')) like :pattern)
                          and (not :team or r.role is not null or u.username in (:configured))
                        order by u.id desc
                        limit :limit
                        """)
                .param("pattern", pattern)
                .param("team", team)
                .param("configured", configured)
                .param("limit", SHOWN)
                .query((rs, row) -> {
                    Timestamp created = rs.getTimestamp("created_at");
                    String username = rs.getString("username");
                    return new AdminUsersDto.User(rs.getLong("id"), username, rs.getString("email"),
                            created == null ? null : created.toInstant(), rs.getBoolean("email_verified"),
                            admins.isConfigured(username) ? Admins.ADMIN : rs.getString("role"), admins.isConfigured(username),
                            rs.getLong("follows"), rs.getLong("label_follows"),
                            rs.getLong("favorites"), rs.getLong("going"), rs.getLong("reviews"), rs.getLong("events_added"));
                })
                .list();
        long total = jdbc.sql("select count(*) from app_user").query(Long.class).single();
        long verified = jdbc.sql("select count(*) from app_user where email_verified").query(Long.class).single();
        long newThisWeek = jdbc.sql("select count(*) from app_user where created_at > :since")
                .param("since", Timestamp.from(clock.instant().minus(Duration.ofDays(7))))
                .query(Long.class).single();
        return new AdminUsersDto(total, verified, newThisWeek, users);
    }
}
