package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.psytrance.psytrance_tracker_backend.util.Texts.blankToNull;

/**
 * Labels of the map as pages their owners write. An admin links the account of someone from the label; the owner
 * then writes the description and links directly, and asks to put artists on the crew or take them off, which an
 * admin decides (the crew is shared data: following a label and its numbers use it). Everything is in the history.
 */
@Service
public class LabelPages {

    public static final String ADD = "ADD";
    public static final String REMOVE = "REMOVE";

    private final JdbcClient jdbc;
    private final MapLabels mapLabels;
    private final ArtistRepository artistRepository;
    private final Admins admins;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;
    private final PageTrust pageTrust;

    public LabelPages(JdbcClient jdbc, MapLabels mapLabels, ArtistRepository artistRepository, Admins admins,
                      AdminChangeRepository changeRepository, Clock clock, PageTrust pageTrust) {
        this.jdbc = jdbc;
        this.mapLabels = mapLabels;
        this.artistRepository = artistRepository;
        this.admins = admins;
        this.changeRepository = changeRepository;
        this.clock = clock;
        this.pageTrust = pageTrust;
    }

    public record Profile(String description, String logoUrl, String websiteUrl, String bandcampUrl, String instagramUrl) {
    }

    /** What everyone sees: the label's own words (null when nobody wrote any) and the crew as it is now */
    public record Page(String name, Profile profile, List<String> crew) {
    }

    public record Request(long id, String label, String kind, String artistName, String username, Instant createdAt) {
    }

    /** The edit page: the page, the owner's waiting crew requests and, for admins, the owner's username */
    public record Edit(String name, Profile profile, List<String> crew, List<Request> pending, String owner) {
    }

    public record Owned(String name) {
    }

    // The crew changes admins approved, read once the database is ready (and again after each decision)
    @EventListener(ApplicationReadyEvent.class)
    public void loadCrew() {
        Map<String, Map<String, Boolean>> changes = new HashMap<>();
        jdbc.sql("select label_name, artist_name, added from label_crew").query(rs -> {
            changes.computeIfAbsent(rs.getString("label_name"), l -> new HashMap<>())
                    .put(rs.getString("artist_name"), rs.getBoolean("added"));
        });
        mapLabels.useCrewChanges(changes);
    }

    public Page page(String label) {
        return new Page(requireLabel(label), profile(label).orElse(null), mapLabels.artists(label).orElseThrow());
    }

    public Edit edit(String label, String username) {
        requireEditor(label, username);
        List<Request> pending = jdbc.sql("""
                        select r.id, r.label_name, r.kind, r.artist_name, u.username, r.created_at
                        from label_requests r join app_user u on u.id = r.user_id
                        where r.label_name = :label and r.status = 'PENDING' order by r.created_at
                        """).param("label", label).query(this::request).list();
        return new Edit(label, profile(label).orElse(null), mapLabels.artists(label).orElseThrow(), pending,
                admins.isAdmin(username) ? owner(label).orElse(null) : null);
    }

    @Transactional
    public Profile saveProfile(String label, Profile profile, String username) {
        requireEditor(label, username);
        Profile clean = new Profile(blankToNull(profile.description()), blankToNull(profile.logoUrl()),
                blankToNull(profile.websiteUrl()), blankToNull(profile.bandcampUrl()), blankToNull(profile.instagramUrl()));
        jdbc.sql("""
                        insert into label_profiles (label_name, description, logo_url, website_url, bandcamp_url, instagram_url, updated_at)
                        values (:label, :description, :logo, :website, :bandcamp, :instagram, :now)
                        on conflict (label_name) do update set description = excluded.description, logo_url = excluded.logo_url,
                          website_url = excluded.website_url, bandcamp_url = excluded.bandcamp_url,
                          instagram_url = excluded.instagram_url, updated_at = excluded.updated_at
                        """)
                .param("label", label).param("description", clean.description()).param("logo", clean.logoUrl())
                .param("website", clean.websiteUrl()).param("bandcamp", clean.bandcampUrl())
                .param("instagram", clean.instagramUrl()).param("now", Timestamp.from(clock.instant()))
                .update();
        log(username, AdminChange.LABEL_PROFILE, label, "", "page written");
        return clean;
    }

    /**
     * The owner asks to put an artist from the line-ups on the crew (ADD) or take one off (REMOVE). From an admin it
     * is done right away. Returns the crew as it is after this.
     */
    @Transactional
    public List<String> requestCrew(String label, String kind, String artistName, String username) {
        requireEditor(label, username);
        if (!ADD.equals(kind) && !REMOVE.equals(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The request is ADD or REMOVE");
        }
        List<String> crew = mapLabels.artists(label).orElseThrow();
        String name;
        if (ADD.equals(kind)) {
            name = artistRepository.findBySlug(ArtistNames.slug(artistName == null ? "" : artistName))
                    .map(artist -> artist.getName())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No artist called " + artistName + " in the line-ups we read"));
            if (crew.stream().anyMatch(n -> ArtistNames.slug(n).equals(ArtistNames.slug(name)))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, name + " is on the label already");
            }
        } else {
            name = crew.stream().filter(n -> n.equalsIgnoreCase(artistName == null ? "" : artistName.strip())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, artistName + " is not on the label"));
        }
        if (pageTrust.trusts(PageTrust.Kind.LABEL, label, username)) {
            applyCrew(label, kind, name, username);
        } else {
            long userId = userId(username);
            jdbc.sql("""
                            insert into label_requests (label_name, kind, artist_name, user_id, status, created_at)
                            values (:label, :kind, :artist, :user, 'PENDING', :now)
                            """)
                    .param("label", label).param("kind", kind).param("artist", name).param("user", userId)
                    .param("now", Timestamp.from(clock.instant())).update();
        }
        return mapLabels.artists(label).orElseThrow();
    }

    /** For admins: every waiting crew request, oldest first */
    public List<Request> pendingRequests() {
        return jdbc.sql("""
                        select r.id, r.label_name, r.kind, r.artist_name, u.username, r.created_at
                        from label_requests r join app_user u on u.id = r.user_id
                        where r.status = 'PENDING' order by r.created_at
                        """).query(this::request).list();
    }

    @Transactional
    public void decide(long id, boolean approve, String adminUsername) {
        Request request = jdbc.sql("""
                        select r.id, r.label_name, r.kind, r.artist_name, u.username, r.created_at
                        from label_requests r join app_user u on u.id = r.user_id where r.id = :id and r.status = 'PENDING'
                        """).param("id", id).query(this::request).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No waiting request " + id));
        if (approve) {
            applyCrew(request.label(), request.kind(), request.artistName(), adminUsername);
        }
        jdbc.sql("update label_requests set status = :status, decided_by = :admin, decided_at = :now where id = :id")
                .param("status", approve ? "APPROVED" : "REJECTED").param("admin", adminUsername)
                .param("now", Timestamp.from(clock.instant())).param("id", id).update();
    }

    @Transactional
    public void setOwner(String label, String ownerUsername, String adminUsername) {
        requireLabel(label);
        long userId = userId(ownerUsername);
        String before = owner(label).orElse("");
        jdbc.sql("""
                        insert into label_owners (label_name, user_id, created_at) values (:label, :user, :now)
                        on conflict (label_name) do update set user_id = excluded.user_id, created_at = excluded.created_at
                        """).param("label", label).param("user", userId).param("now", Timestamp.from(clock.instant())).update();
        if (!before.equals(ownerUsername)) {
            pageTrust.reset(PageTrust.Kind.LABEL, label);
        }
        log(adminUsername, AdminChange.LABEL_OWNER, label, before, ownerUsername);
    }

    @Transactional
    public void removeOwner(String label, String adminUsername) {
        String before = owner(label).orElse("");
        jdbc.sql("delete from label_owners where label_name = :label").param("label", label).update();
        log(adminUsername, AdminChange.LABEL_OWNER, label, before, "");
    }

    /** The labels this account owns, for its profile */
    public List<Owned> ownedBy(String username) {
        return jdbc.sql("""
                        select o.label_name from label_owners o join app_user u on u.id = o.user_id
                        where u.username = :username order by o.label_name
                        """).param("username", username).query((rs, row) -> new Owned(rs.getString(1))).list();
    }

    public boolean canEdit(String label, String username) {
        return username != null && (admins.isAdmin(username) || owner(label).map(username::equals).orElse(false));
    }

    // ---- Helpers ----

    private void applyCrew(String label, String kind, String artistName, String username) {
        jdbc.sql("""
                        insert into label_crew (label_name, artist_name, added, decided_by, decided_at)
                        values (:label, :artist, :added, :admin, :now)
                        on conflict (label_name, artist_name) do update set added = excluded.added,
                          decided_by = excluded.decided_by, decided_at = excluded.decided_at
                        """)
                .param("label", label).param("artist", artistName).param("added", ADD.equals(kind))
                .param("admin", username).param("now", Timestamp.from(clock.instant())).update();
        log(username, AdminChange.LABEL_CREW, label, "", (ADD.equals(kind) ? "+ " : "- ") + artistName);
        loadCrew();
    }

    private Optional<Profile> profile(String label) {
        return jdbc.sql("""
                        select description, logo_url, website_url, bandcamp_url, instagram_url from label_profiles
                        where label_name = :label
                        """).param("label", label)
                .query((rs, row) -> new Profile(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)))
                .optional();
    }

    private Optional<String> owner(String label) {
        return jdbc.sql("""
                        select u.username from label_owners o join app_user u on u.id = o.user_id where o.label_name = :label
                        """).param("label", label).query(String.class).optional();
    }

    private Request request(ResultSet rs, int row) throws SQLException {
        return new Request(rs.getLong("id"), rs.getString("label_name"), rs.getString("kind"), rs.getString("artist_name"),
                rs.getString("username"), rs.getTimestamp("created_at").toInstant());
    }

    private String requireLabel(String label) {
        if (!mapLabels.isLabel(label)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No label called " + label);
        }
        return label;
    }

    // The label, for an admin or its owner; guests get 401 and other users 403
    private void requireEditor(String label, String username) {
        requireLabel(label);
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first");
        }
        if (!canEdit(label, username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the label and admins can edit this page");
        }
    }

    private long userId(String username) {
        return jdbc.sql("select id from app_user where username = :username").param("username", username)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account called " + username));
    }

    private void log(String username, String kind, String target, String before, String after) {
        changeRepository.save(new AdminChange(username, kind, target, before, after, clock.instant()));
    }
}
