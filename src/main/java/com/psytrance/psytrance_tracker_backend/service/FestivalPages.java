package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static com.psytrance.psytrance_tracker_backend.util.Texts.blankToNull;

/**
 * Festivals of the map as pages their owners write: their own words, links (site, tickets, Instagram) and the
 * organizer who runs the festival, so the festival's page leads to the organizer's. An admin links the owner.
 */
@Service
public class FestivalPages {

    private final JdbcClient jdbc;
    private final MapFestivals mapFestivals;
    private final Admins admins;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public FestivalPages(JdbcClient jdbc, MapFestivals mapFestivals, Admins admins, AdminChangeRepository changeRepository,
                         Clock clock) {
        this.jdbc = jdbc;
        this.mapFestivals = mapFestivals;
        this.admins = admins;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    public record Profile(String description, String logoUrl, String websiteUrl, String ticketUrl, String instagramUrl, String organizerSlug) {
    }

    /** The organizer who runs the festival, for a link to their page */
    public record Organizer(String slug, String name) {
    }

    public record Page(String id, String name, Profile profile, Organizer organizer) {
    }

    public record Edit(String id, String name, Profile profile, Organizer organizer, String owner) {
    }

    public record Owned(String id, String name) {
    }

    public Page page(String id) {
        String name = requireFestival(id);
        Profile profile = profile(id).orElse(null);
        return new Page(id, name, profile, organizer(profile));
    }

    public Edit edit(String id, String username) {
        String name = requireEditor(id, username);
        Profile profile = profile(id).orElse(null);
        return new Edit(id, name, profile, organizer(profile), admins.isAdmin(username) ? owner(id).orElse(null) : null);
    }

    @Transactional
    public Page saveProfile(String id, Profile profile, String username) {
        String name = requireEditor(id, username);
        String organizerSlug = blankToNull(profile.organizerSlug()) == null ? null : ArtistNames.slug(profile.organizerSlug());
        if (organizerSlug != null && jdbc.sql("select count(*) from organizers where slug = :slug")
                .param("slug", organizerSlug).query(Long.class).single() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No organizer page called " + profile.organizerSlug());
        }
        jdbc.sql("""
                        insert into festival_profiles (festival_id, description, logo_url, website_url, ticket_url, instagram_url, organizer_slug, updated_at)
                        values (:id, :description, :logo, :website, :tickets, :instagram, :organizer, :now)
                        on conflict (festival_id) do update set description = excluded.description, logo_url = excluded.logo_url,
                          website_url = excluded.website_url, ticket_url = excluded.ticket_url,
                          instagram_url = excluded.instagram_url, organizer_slug = excluded.organizer_slug,
                          updated_at = excluded.updated_at
                        """)
                .param("id", id).param("description", blankToNull(profile.description())).param("logo", blankToNull(profile.logoUrl()))
                .param("website", blankToNull(profile.websiteUrl())).param("tickets", blankToNull(profile.ticketUrl()))
                .param("instagram", blankToNull(profile.instagramUrl())).param("organizer", organizerSlug)
                .param("now", Timestamp.from(clock.instant())).update();
        log(username, AdminChange.FESTIVAL_PROFILE, name, "", "page written");
        return page(id);
    }

    @Transactional
    public void setOwner(String id, String ownerUsername, String adminUsername) {
        String name = requireFestival(id);
        long userId = jdbc.sql("select id from app_user where username = :username").param("username", ownerUsername)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account called " + ownerUsername));
        String before = owner(id).orElse("");
        jdbc.sql("""
                        insert into festival_owners (festival_id, user_id, created_at) values (:id, :user, :now)
                        on conflict (festival_id) do update set user_id = excluded.user_id, created_at = excluded.created_at
                        """).param("id", id).param("user", userId).param("now", Timestamp.from(clock.instant())).update();
        log(adminUsername, AdminChange.FESTIVAL_OWNER, name, before, ownerUsername);
    }

    @Transactional
    public void removeOwner(String id, String adminUsername) {
        String name = requireFestival(id);
        String before = owner(id).orElse("");
        jdbc.sql("delete from festival_owners where festival_id = :id").param("id", id).update();
        log(adminUsername, AdminChange.FESTIVAL_OWNER, name, before, "");
    }

    /** The festivals this account owns, for its profile */
    public List<Owned> ownedBy(String username) {
        return jdbc.sql("""
                        select o.festival_id from festival_owners o join app_user u on u.id = o.user_id
                        where u.username = :username order by o.festival_id
                        """).param("username", username).query(String.class).list().stream()
                .map(id -> new Owned(id, mapFestivals.name(id).orElse(id)))
                .toList();
    }

    public boolean canEdit(String id, String username) {
        return username != null && (admins.isAdmin(username) || owner(id).map(username::equals).orElse(false));
    }

    private Organizer organizer(Profile profile) {
        if (profile == null || profile.organizerSlug() == null) {
            return null;
        }
        return jdbc.sql("select slug, name from organizers where slug = :slug").param("slug", profile.organizerSlug())
                .query((rs, row) -> new Organizer(rs.getString(1), rs.getString(2))).optional().orElse(null);
    }

    private Optional<Profile> profile(String id) {
        return jdbc.sql("""
                        select description, logo_url, website_url, ticket_url, instagram_url, organizer_slug from festival_profiles
                        where festival_id = :id
                        """).param("id", id)
                .query((rs, row) -> new Profile(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)))
                .optional();
    }

    private Optional<String> owner(String id) {
        return jdbc.sql("""
                        select u.username from festival_owners o join app_user u on u.id = o.user_id where o.festival_id = :id
                        """).param("id", id).query(String.class).optional();
    }

    private String requireFestival(String id) {
        return mapFestivals.name(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No festival called " + id));
    }

    // The festival's name, for an admin or its owner; guests get 401 and other users 403
    private String requireEditor(String id, String username) {
        String name = requireFestival(id);
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first");
        }
        if (!canEdit(id, username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the festival and admins can edit this page");
        }
        return name;
    }

    private void log(String username, String kind, String target, String before, String after) {
        changeRepository.save(new AdminChange(username, kind, target, before, after, clock.instant()));
    }
}
