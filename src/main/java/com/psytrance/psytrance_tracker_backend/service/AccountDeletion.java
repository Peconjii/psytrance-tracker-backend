package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.OurEvent;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * "Delete my account" (the right to be forgotten): the account and everything that is the person's go for good:
 * follows, saved events, "I'm going", reviews, the pages they owned (the pages stay, without an owner), their
 * requests and claims, the events they added (off the site and the archive too) and their role. Feedback they sent
 * stays as a message without the account. The admins' history keeps the username of what they changed then.
 */
@Service
public class AccountDeletion {

    // The account's own rows, by user_id
    private static final List<String> OWN_ROWS = List.of("artist_follows", "label_follows", "favorites", "attendances",
            "reviews", "event_reminders", "follow_notices", "artist_owners", "organizer_owners", "label_owners",
            "festival_owners", "artist_requests", "label_requests", "page_claims", "page_requests", "user_roles",
            "password_reset_tokens", "email_verification_tokens");
    // What the site keeps about an event, by event_id (favorites and reviews keep it as text)
    private static final List<String> EVENT_ROWS = List.of("artist_events", "event_genres", "event_timetable",
            "lineup_scans", "attendances", "event_reminders", "follow_notices", "featured_events", "hidden_events",
            "festival_editions", "event_summaries");

    private final JdbcClient jdbc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventService eventService;

    public AccountDeletion(JdbcClient jdbc, UserRepository userRepository, PasswordEncoder passwordEncoder,
                           EventService eventService) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventService = eventService;
    }

    /** The password again, so a phone left open can't lose someone their account */
    @Transactional
    public void delete(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That's not your password");
        }
        long id = user.getId();

        // The events they added, with what the site kept about them
        List<Long> events = jdbc.sql("select id from our_events where submitted_by = :user").param("user", id)
                .query(Long.class).list().stream().map(eventId -> OurEvent.ID_BASE + eventId).toList();
        if (!events.isEmpty()) {
            for (String table : EVENT_ROWS) {
                jdbc.sql("delete from " + table + " where event_id in (:events)").param("events", events).update();
            }
            List<String> asText = events.stream().map(String::valueOf).toList();
            jdbc.sql("delete from favorites where event_id in (:events)").param("events", asText).update();
            jdbc.sql("delete from reviews where event_id in (:events)").param("events", asText).update();
            jdbc.sql("delete from our_events where submitted_by = :user").param("user", id).update();
        }

        for (String table : OWN_ROWS) {
            jdbc.sql("delete from " + table + " where user_id = :user").param("user", id).update();
        }
        jdbc.sql("update feedback set user_id = null where user_id = :user").param("user", id).update();
        jdbc.sql("delete from app_user where id = :user").param("user", id).update();

        // The added events leave the lists and the map
        events.forEach(eventService::refreshSoon);
    }
}
