package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * "Claim this page": an account with a confirmed email asks to own an artist's, organizer's, label's or festival's
 * page. When the email is on the domain of the page's official website (or is the artist's booking email), it is
 * approved at once; otherwise an admin decides with the link the claimer sent. Approving links the owner the same way
 * an admin does by hand, so it is in the history.
 */
@Service
public class PageClaims {

    public static final List<String> KINDS = List.of("ARTIST", "ORGANIZER", "LABEL", "FESTIVAL");
    /** Sites anyone can have a page on: an email there proves nothing */
    private static final Set<String> SHARED_HOSTS = Set.of("bandcamp.com", "soundcloud.com", "instagram.com",
            "facebook.com", "linktr.ee", "google.com", "gmail.com", "wixsite.com", "blogspot.com", "wordpress.com",
            "spotify.com", "youtube.com", "beatport.com", "goabase.net", "carrd.co", "squarespace.com");

    private final JdbcClient jdbc;
    private final ArtistEditing artistEditing;
    private final OrganizerEditing organizerEditing;
    private final LabelPages labelPages;
    private final FestivalPages festivalPages;
    private final MapLabels mapLabels;
    private final MapFestivals mapFestivals;
    private final Clock clock;

    public PageClaims(JdbcClient jdbc, ArtistEditing artistEditing, OrganizerEditing organizerEditing,
                      LabelPages labelPages, FestivalPages festivalPages, MapLabels mapLabels, MapFestivals mapFestivals,
                      Clock clock) {
        this.jdbc = jdbc;
        this.artistEditing = artistEditing;
        this.organizerEditing = organizerEditing;
        this.labelPages = labelPages;
        this.festivalPages = festivalPages;
        this.mapLabels = mapLabels;
        this.mapFestivals = mapFestivals;
        this.clock = clock;
    }

    public record Claim(long id, String kind, String target, String username, String email, String proofUrl,
                        String message, String status, Instant createdAt, String rejectReason) {
    }

    @Transactional
    public Claim claim(String kind, String target, String proofUrl, String message, String username) {
        if (!KINDS.contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The kind is one of " + String.join(", ", KINDS));
        }
        String key = key(kind, target);
        User user = user(username);
        if (!user.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Confirm your email first: open the link we sent you");
        }
        if (owner(kind, key).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This page has an owner already. If that's wrong, write to us through the feedback form.");
        }
        boolean waiting = jdbc.sql("""
                        select count(*) from page_claims where kind = :kind and target = :target and user_id = :user and status = 'PENDING'
                        """).param("kind", kind).param("target", key).param("user", user.id()).query(Long.class).single() > 0;
        if (waiting) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You asked for this page already; an admin will look at it");
        }
        boolean proven = provenByEmail(kind, key, user.email());
        Instant now = clock.instant();
        long id = jdbc.sql("""
                        insert into page_claims (kind, target, user_id, proof_url, message, status, created_at, decided_by, decided_at)
                        values (:kind, :target, :user, :proof, :message, :status, :now, :decidedBy, :decidedAt)
                        returning id
                        """)
                .param("kind", kind).param("target", key).param("user", user.id())
                .param("proof", blankToNull(proofUrl)).param("message", blankToNull(message))
                .param("status", proven ? "APPROVED" : "PENDING").param("now", Timestamp.from(now))
                .param("decidedBy", proven ? "email domain" : null).param("decidedAt", proven ? Timestamp.from(now) : null)
                .query(Long.class).single();
        if (proven) {
            link(kind, key, username, "email domain");
        }
        return find(id).orElseThrow();
    }

    /** The claims of this account, newest first */
    public List<Claim> mine(String username) {
        return jdbc.sql(SELECT + " where u.username = :username order by c.created_at desc")
                .param("username", username).query(this::claim).list();
    }

    /** For admins: the claims waiting for a decision, oldest first */
    public List<Claim> pending() {
        return jdbc.sql(SELECT + " where c.status = 'PENDING' order by c.created_at").query(this::claim).list();
    }

    @Transactional
    public Claim decide(long id, boolean approve, String reason, String adminUsername) {
        Claim claim = find(id).filter(c -> "PENDING".equals(c.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No waiting claim " + id));
        if (approve) {
            if (owner(claim.kind(), claim.target()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This page has an owner already: unlink them first");
            }
            link(claim.kind(), claim.target(), claim.username(), adminUsername);
        }
        jdbc.sql("""
                        update page_claims set status = :status, decided_by = :admin, decided_at = :now, reject_reason = :reason
                        where id = :id
                        """)
                .param("status", approve ? "APPROVED" : "REJECTED").param("admin", adminUsername)
                .param("now", Timestamp.from(clock.instant())).param("reason", approve ? null : blankToNull(reason))
                .param("id", id).update();
        return find(id).orElseThrow();
    }

    // ---- Helpers ----

    private static final String SELECT = """
            select c.id, c.kind, c.target, u.username, u.email, c.proof_url, c.message, c.status, c.created_at, c.reject_reason
            from page_claims c join app_user u on u.id = c.user_id
            """;

    private Optional<Claim> find(long id) {
        return jdbc.sql(SELECT + " where c.id = :id").param("id", id).query(this::claim).optional();
    }

    private Claim claim(ResultSet rs, int row) throws SQLException {
        return new Claim(rs.getLong("id"), rs.getString("kind"), rs.getString("target"), rs.getString("username"),
                rs.getString("email"), rs.getString("proof_url"), rs.getString("message"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("reject_reason"));
    }

    // The page's key as it is stored, after checking the page exists: artists and organizers by slug
    private String key(String kind, String target) {
        String value = target == null ? "" : target.strip();
        boolean exists = switch (kind) {
            case "ARTIST" -> {
                value = ArtistNames.slug(value);
                yield count("select count(*) from artists where slug = :key", value) > 0;
            }
            case "ORGANIZER" -> {
                value = ArtistNames.slug(value);
                yield count("select count(*) from organizers where slug = :key", value) > 0;
            }
            case "LABEL" -> mapLabels.isLabel(value);
            default -> mapFestivals.name(value).isPresent();
        };
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such page to claim");
        }
        return value;
    }

    private Optional<String> owner(String kind, String key) {
        String sql = switch (kind) {
            case "ARTIST" -> "select u.username from artist_owners o join app_user u on u.id = o.user_id where o.artist_slug = :key";
            case "ORGANIZER" -> "select u.username from organizer_owners o join app_user u on u.id = o.user_id where o.organizer_slug = :key";
            case "LABEL" -> "select u.username from label_owners o join app_user u on u.id = o.user_id where o.label_name = :key";
            default -> "select u.username from festival_owners o join app_user u on u.id = o.user_id where o.festival_id = :key";
        };
        return jdbc.sql(sql).param("key", key).query(String.class).optional();
    }

    // The email is on the official website's domain (not a shared host), or is the artist's booking email
    private boolean provenByEmail(String kind, String key, String email) {
        if (email == null || !email.contains("@")) {
            return false;
        }
        String address = email.strip().toLowerCase(Locale.ROOT);
        String domain = address.substring(address.indexOf('@') + 1);
        if ("ARTIST".equals(kind)) {
            String booking = text("select booking_email from artist_profiles where slug = :key", key);
            if (booking != null && booking.strip().equalsIgnoreCase(address)) {
                return true;
            }
        }
        String site = switch (kind) {
            case "ARTIST" -> text("select website_url from artist_profiles where slug = :key", key);
            case "ORGANIZER" -> text("select coalesce(website_url, url) from organizers where slug = :key", key);
            case "LABEL" -> text("select website_url from label_profiles where label_name = :key", key);
            default -> text("select website_url from festival_profiles where festival_id = :key", key);
        };
        String host = host(site);
        if (host == null || SHARED_HOSTS.stream().anyMatch(shared -> host.equals(shared) || host.endsWith("." + shared))
                || SHARED_HOSTS.contains(domain)) {
            return false;
        }
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String value = url.strip();
            String host = URI.create(value.contains("://") ? value : "https://" + value).getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void link(String kind, String key, String username, String by) {
        switch (kind) {
            case "ARTIST" -> artistEditing.setOwner(key, username, by);
            case "ORGANIZER" -> organizerEditing.setOwner(key, username, by);
            case "LABEL" -> labelPages.setOwner(key, username, by);
            default -> festivalPages.setOwner(key, username, by);
        }
    }

    private record User(long id, String email, boolean emailVerified) {
    }

    private User user(String username) {
        return jdbc.sql("select id, email, email_verified from app_user where username = :username")
                .param("username", username)
                .query((rs, row) -> new User(rs.getLong(1), rs.getString(2), rs.getBoolean(3))).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private long count(String sql, String key) {
        return jdbc.sql(sql).param("key", key).query(Long.class).single();
    }

    private String text(String sql, String key) {
        return jdbc.sql(sql).param("key", key).query(String.class).optional().orElse(null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
