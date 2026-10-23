package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What a shared link shows in WhatsApp, Telegram, Facebook, Discord and the like. Their bots don't run JavaScript, so
 * they never see the page's own title and picture; vercel.json sends only those bots here (by their user agent), and
 * this answers with a small page that has the artist's, event's, label's, festival's or organizer's name, a line about
 * it and its picture. People never land here; if one does, the page sends them on to the real one.
 */
@Service
public class SharePreview {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final EventService eventService;
    private final MapLabels mapLabels;
    private final MapFestivals mapFestivals;
    private final Clock clock;
    private final String site;

    public SharePreview(JdbcClient jdbc, EventService eventService, MapLabels mapLabels, MapFestivals mapFestivals,
                        Clock clock, @Value("${app.frontend-url}") String frontendUrl) {
        this.jdbc = jdbc;
        this.eventService = eventService;
        this.mapLabels = mapLabels;
        this.mapFestivals = mapFestivals;
        this.clock = clock;
        this.site = frontendUrl.replaceAll("/+$", "");
    }

    record Card(String title, String description, String image, String path) {
    }

    /** kind: artists, events, labels, festivals or organizers; key: the slug, id or name of the page */
    public String html(String kind, String key) {
        Optional<Card> found = switch (kind) {
            case "artists" -> artist(key);
            case "events" -> event(key);
            case "labels" -> label(key);
            case "festivals" -> festival(key);
            case "organizers" -> organizer(key);
            default -> Optional.empty();
        };
        Card card = found.orElse(new Card("Map of Psy", "Psytrance as a galaxy: genres, artists, labels and the parties happening now.",
                null, "/"));
        String url = site + card.path();
        String image = card.image() != null && card.image().startsWith("https://") ? card.image() : site + "/og-image.png";
        String title = card.title() + " · Map of Psy";
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <title>%1$s</title>
                <meta name="description" content="%2$s">
                <meta property="og:type" content="website">
                <meta property="og:site_name" content="Map of Psy">
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="og:url" content="%3$s">
                <meta property="og:image" content="%4$s">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:title" content="%1$s">
                <meta name="twitter:description" content="%2$s">
                <meta name="twitter:image" content="%4$s">
                <link rel="canonical" href="%3$s">
                <meta http-equiv="refresh" content="0; url=%3$s">
                </head><body><a href="%3$s">%1$s</a></body></html>
                """.formatted(escape(title), escape(cut(card.description())), escape(url), escape(image));
    }

    // ---- The pages ----

    private Optional<Card> artist(String slug) {
        return jdbc.sql("""
                        select a.name, p.bio, p.photo_url,
                               (select count(distinct ae.event_id) from artist_events ae join event_summaries s on s.event_id = ae.event_id
                                where ae.artist_id = a.id and coalesce(s.date_end, s.date_start) >= :today) as upcoming
                        from artists a left join artist_profiles p on p.slug = a.slug where a.slug = :slug
                        """)
                .param("slug", slug).param("today", Date.valueOf(LocalDate.now(clock)))
                .query((rs, row) -> {
                    long upcoming = rs.getLong("upcoming");
                    String about = rs.getString("bio") != null ? rs.getString("bio")
                            : upcoming == 0 ? "Psytrance artist on Map of Psy." : "Psytrance artist: " + upcoming
                                    + (upcoming == 1 ? " gig" : " gigs") + " coming up.";
                    return new Card(rs.getString("name"), about, rs.getString("photo_url"), "/artists/" + encode(slug));
                })
                .optional();
    }

    private Optional<Card> event(String key) {
        long id;
        try {
            id = Long.parseLong(key);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        // The flyer comes from Goabase's list; the name, date and place from what the site kept (past events too)
        Optional<EventDto> listed = listed(id);
        String flyer = listed.map(EventDto::urlImageMedium).orElse(null);
        Optional<Card> kept = jdbc.sql("select name, date_start, town, country from event_summaries where event_id = :id")
                .param("id", id)
                .query((rs, row) -> {
                    Date start = rs.getDate("date_start");
                    String when = start == null ? null : start.toLocalDate().format(DAY);
                    return new Card(rs.getString("name"), join(" · ", when, join(", ", rs.getString("town"), rs.getString("country"))),
                            flyer, "/events/" + id);
                })
                .optional();
        return kept.or(() -> listed.map(event -> new Card(event.nameParty(), join(", ", event.nameTown(), event.nameCountry()),
                flyer, "/events/" + id)));
    }

    private Optional<Card> label(String name) {
        if (!mapLabels.isLabel(name)) {
            return Optional.empty();
        }
        int crew = mapLabels.artists(name).map(List::size).orElse(0);
        record Profile(String description, String logo) {
        }
        Profile profile = jdbc.sql("select description, logo_url from label_profiles where label_name = :name").param("name", name)
                .query((rs, row) -> new Profile(rs.getString(1), rs.getString(2))).optional().orElse(new Profile(null, null));
        return Optional.of(new Card(name, profile.description() != null ? profile.description()
                : "Psytrance label: " + crew + " artists and where they play next.", profile.logo(), "/labels/" + encode(name)));
    }

    private Optional<Card> festival(String id) {
        return mapFestivals.name(id).map(name -> {
            String description = jdbc.sql("select description from festival_profiles where festival_id = :id").param("id", id)
                    .query(String.class).optional().orElse("Psytrance festival: its sound, sets and editions on Map of Psy.");
            return new Card(name, description, null, "/festivals/" + encode(id));
        });
    }

    private Optional<Card> organizer(String slug) {
        return jdbc.sql("select name, bio, photo_url from organizers where slug = :slug").param("slug", slug)
                .query((rs, row) -> new Card(rs.getString("name"),
                        rs.getString("bio") != null ? rs.getString("bio") : "Psytrance organizer: their parties and festivals.",
                        rs.getString("photo_url"), "/organizers/" + encode(slug)))
                .optional();
    }

    // ---- Helpers ----

    // The event as Goabase lists it; empty when it is gone or Goabase can't be reached
    private Optional<EventDto> listed(long id) {
        try {
            return Optional.of(eventService.findById(id));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String cut(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return flat.length() <= 200 ? flat : flat.substring(0, 197) + "…";
    }

    // The parts that are there, joined: join(", ", "Vienna", null) is "Vienna"
    private static String join(String separator, String... parts) {
        return Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).map(String::strip)
                .collect(Collectors.joining(separator));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
