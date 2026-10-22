package com.psytrance.psytrance_tracker_backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One search box for the whole site: artists, organizers, upcoming events, labels and festivals whose name has the
 * words in it. The map's genres and map-only artists are searched in the browser, from the map data it already has.
 */
@Service
public class SiteSearch {

    private static final int EACH = 8;

    private final JdbcClient jdbc;
    private final MapLabels mapLabels;
    private final MapFestivals mapFestivals;
    private final Clock clock;

    public SiteSearch(JdbcClient jdbc, MapLabels mapLabels, MapFestivals mapFestivals, Clock clock) {
        this.jdbc = jdbc;
        this.mapLabels = mapLabels;
        this.mapFestivals = mapFestivals;
        this.clock = clock;
    }

    public record Artist(String slug, String name, long upcomingEvents) {
    }

    public record Organizer(String slug, String name) {
    }

    public record Event(long id, String name, LocalDate dateStart, String town, String country) {
    }

    public record Festival(String id, String name) {
    }

    public record Results(String query, List<Artist> artists, List<Organizer> organizers, List<Event> events,
                          List<String> labels, List<Festival> festivals) {
    }

    public Results search(String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.length() < 2 || q.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search for 2 to 60 letters");
        }
        String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        LocalDate today = LocalDate.now(clock);

        // Exact names first, then the busiest; words admins marked as no artist are left out
        List<Artist> artists = jdbc.sql("""
                        select a.slug, a.name,
                               (select count(distinct ae.event_id) from artist_events ae
                                join event_summaries s on s.event_id = ae.event_id
                                where ae.artist_id = a.id and coalesce(s.date_end, s.date_start) >= :today
                                  and ae.event_id not in (%s)) as upcoming
                        from artists a
                        where lower(a.name) like :like escape '\\'
                          and a.slug not in (select slug from artist_blocklist)
                        order by (lower(a.name) = :q) desc, upcoming desc, a.name
                        limit :each
                        """.formatted(Stats.LEFT_OUT))
                .param("like", like).param("q", q).param("today", Date.valueOf(today)).param("each", EACH)
                .query((rs, row) -> new Artist(rs.getString("slug"), rs.getString("name"), rs.getLong("upcoming")))
                .list();

        List<Organizer> organizers = jdbc.sql("""
                        select slug, name from organizers where lower(name) like :like escape '\\'
                        order by (lower(name) = :q) desc, name limit :each
                        """)
                .param("like", like).param("q", q).param("each", EACH)
                .query((rs, row) -> new Organizer(rs.getString("slug"), rs.getString("name"))).list();

        // Upcoming only, soonest first: by name or by town
        List<Event> events = jdbc.sql("""
                        select s.event_id, s.name, s.date_start, s.town, s.country from event_summaries s
                        where (lower(s.name) like :like escape '\\' or lower(coalesce(s.town, '')) like :like escape '\\')
                          and coalesce(s.date_end, s.date_start) >= :today
                          and s.event_id not in (%s)
                        order by s.date_start, s.name
                        limit :each
                        """.formatted(Stats.LEFT_OUT))
                .param("like", like).param("today", Date.valueOf(today)).param("each", EACH)
                .query((rs, row) -> {
                    Date start = rs.getDate("date_start");
                    return new Event(rs.getLong("event_id"), rs.getString("name"), start == null ? null : start.toLocalDate(),
                            rs.getString("town"), rs.getString("country"));
                })
                .list();

        List<String> labels = mapLabels.names().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing((String name) -> !name.toLowerCase(Locale.ROOT).startsWith(q)).thenComparing(name -> name))
                .limit(EACH).toList();
        List<Festival> festivals = mapFestivals.all().entrySet().stream()
                .filter(e -> e.getValue().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing((Map.Entry<String, String> e) -> !e.getValue().toLowerCase(Locale.ROOT).startsWith(q))
                        .thenComparing(Map.Entry::getValue))
                .limit(EACH).map(e -> new Festival(e.getKey(), e.getValue())).toList();

        return new Results(q, artists, organizers, events, labels, festivals);
    }
}
