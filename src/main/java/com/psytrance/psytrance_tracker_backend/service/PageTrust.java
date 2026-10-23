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
import java.util.Optional;

/**
 * What an admin trusts a page's owner with, per page (Act One's owner is trusted with Act One only). A trusted owner's
 * changes go live without waiting for an admin:
 * <ul>
 *   <li>ARTIST: a new name or genres;</li>
 *   <li>ORGANIZER: events they add with the organizer's name;</li>
 *   <li>LABEL: artists joining or leaving the crew;</li>
 *   <li>FESTIVAL: which events are the festival's editions.</li>
 * </ul>
 * DJs, producers and live acts are trusted the same way: the role is a label on the page, not a set of rights.
 * A new owner starts untrusted; admins are trusted everywhere.
 */
@Service
public class PageTrust {

    public enum Kind {
        ARTIST("artist_owners", "artist_slug"),
        ORGANIZER("organizer_owners", "organizer_slug"),
        LABEL("label_owners", "label_name"),
        FESTIVAL("festival_owners", "festival_id");

        private final String table;
        private final String key;

        Kind(String table, String key) {
            this.table = table;
            this.key = key;
        }
    }

    /** owner: the owner's username, or null when the page has none */
    public record Trust(String owner, boolean trusted) {
    }

    private final JdbcClient jdbc;
    private final Admins admins;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public PageTrust(JdbcClient jdbc, Admins admins, AdminChangeRepository changeRepository, Clock clock) {
        this.jdbc = jdbc;
        this.admins = admins;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    public static Kind kind(String name) {
        try {
            return Kind.valueOf(name == null ? "" : name.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind is ARTIST, ORGANIZER, LABEL or FESTIVAL");
        }
    }

    /** Whether this account's changes to the page skip the admins: an admin, or the page's trusted owner */
    public boolean trusts(Kind kind, String key, String username) {
        if (username == null) {
            return false;
        }
        return admins.isAdmin(username) || find(kind, key).filter(t -> username.equals(t.owner()) && t.trusted()).isPresent();
    }

    /** The page's owner and trust, for its owner and admins; empty when it has no owner */
    public Optional<Trust> find(Kind kind, String key) {
        return jdbc.sql("select u.username, o.trusted from %s o join app_user u on u.id = o.user_id where o.%s = :key"
                        .formatted(kind.table, kind.key))
                .param("key", key)
                .query((rs, row) -> new Trust(rs.getString(1), rs.getBoolean(2)))
                .optional();
    }

    /** An admin trusts the owner, or stops; 404 when the page has no owner */
    @Transactional
    public Trust set(Kind kind, String key, boolean trusted, String adminUsername) {
        Trust before = find(kind, key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This page has no owner to trust"));
        jdbc.sql("update %s set trusted = :trusted where %s = :key".formatted(kind.table, kind.key))
                .param("trusted", trusted).param("key", key).update();
        if (before.trusted() != trusted) {
            changeRepository.save(new AdminChange(adminUsername, AdminChange.PAGE_TRUSTED, kind + ": " + key,
                    label(before), label(new Trust(before.owner(), trusted)), clock.instant()));
        }
        return new Trust(before.owner(), trusted);
    }

    /** A page that changes hands starts untrusted again (the old owner's trust isn't the new one's) */
    public void reset(Kind kind, String key) {
        jdbc.sql("update %s set trusted = false where %s = :key".formatted(kind.table, kind.key)).param("key", key).update();
    }

    private static String label(Trust trust) {
        return trust.owner() + (trust.trusted() ? " · trusted" : "");
    }
}
