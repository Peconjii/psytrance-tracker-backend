package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.model.OurEvent;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Numbers for the artist and label pages, counted with SQL from what the site keeps: the gigs read from line-ups
 * (event_summaries, kept since the site started reading them in October 2026), the events' map genres and the follows.
 * Hidden events and added events that aren't approved don't count.
 */
@Service
public class Stats {

    private final JdbcClient jdbc;
    private final ArtistRepository artistRepository;
    private final MapLabels mapLabels;
    private final Clock clock;

    public Stats(JdbcClient jdbc, ArtistRepository artistRepository, MapLabels mapLabels, Clock clock) {
        this.jdbc = jdbc;
        this.artistRepository = artistRepository;
        this.mapLabels = mapLabels;
        this.clock = clock;
    }

    /** How many of something: gigs per country, per genre, per year */
    public record Count(String key, long count) {
    }

    /** Followers who started following in a month ("2026-10"), and how many followed by its end */
    public record Month(String month, long added, long total) {
    }

    public record Gigs(long total, long upcoming, long past, long live, List<Count> byCountry, List<Count> byGenre,
                       List<Count> byYear, LocalDate countedSince) {
    }

    public record ArtistStats(String slug, Gigs gigs, long followers, List<Month> followersByMonth) {
    }

    /** An upcoming event where artists of a label play */
    public record LabelGig(long eventId, String name, LocalDate dateStart, String town, String country, List<String> artists) {
    }

    /** upcoming: the crew's next gigs; past: the last ones the site read (newest first) */
    public record LabelStats(String label, int crew, int crewWithGigs, Gigs gigs, long followers,
                             List<Month> followersByMonth, List<LabelGig> upcoming, List<LabelGig> past) {
    }

    /** A label in the list of all labels: how big its crew is, how busy and how followed */
    public record LabelSummary(String label, int crew, long upcomingGigs, long followers) {
    }

    public ArtistStats artist(String slug) {
        Artist artist = artistRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found"));
        return new ArtistStats(artist.getSlug(), gigs(List.of(artist.getId())),
                count("select count(*) from artist_follows where artist_slug = :key", artist.getSlug()),
                months("select created_at from artist_follows where artist_slug = :key", artist.getSlug()));
    }

    /** The crew of a label of the map together: their gigs (each event once) and the label's followers */
    public LabelStats label(String label) {
        List<String> crew = mapLabels.artists(label)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No label called " + label));
        List<Long> ids = new ArrayList<>();
        for (String name : crew) {
            artistRepository.findBySlug(ArtistNames.slug(name))
                    .ifPresent(artist -> ids.add(artist.getId()));
        }
        int withGigs = ids.isEmpty() ? 0 : jdbc.sql("select count(distinct artist_id) from artist_events where artist_id in (:ids)")
                .param("ids", ids).query(Long.class).single().intValue();
        return new LabelStats(label, crew.size(), withGigs, gigs(ids),
                count("select count(*) from label_follows where label_name = :key", label),
                months("select created_at from label_follows where label_name = :key", label), crewGigs(ids, false), crewGigs(ids, true));
    }

    // Events that don't count: hidden by an admin, and events added on our site that aren't (or are no longer) approved
    static final String LEFT_OUT = "select event_id from hidden_events union all "
            + "select " + OurEvent.ID_BASE + " + id from our_events where status <> '" + OurEvent.APPROVED + "'";

    // The gigs of these artists, each event once, without the ones left out
    private Gigs gigs(Collection<Long> artistIds) {
        LocalDate today = LocalDate.now(clock);
        LocalDate since = jdbc.sql("select min(date_start) from event_summaries").query(Date.class).optional()
                .map(Date::toLocalDate).orElse(null);
        if (artistIds.isEmpty()) {
            return new Gigs(0, 0, 0, 0, List.of(), List.of(), List.of(), since);
        }
        String events = """
                select distinct s.event_id, s.date_start, coalesce(s.date_end, s.date_start) as last_day, s.country
                from artist_events ae join event_summaries s on s.event_id = ae.event_id
                where ae.artist_id in (:ids) and s.event_id not in (%s)
                """.formatted(LEFT_OUT);
        long total = jdbc.sql("select count(*) from (" + events + ") e").param("ids", artistIds).query(Long.class).single();
        long upcoming = jdbc.sql("select count(*) from (" + events + ") e where e.last_day >= :today")
                .param("ids", artistIds).param("today", Date.valueOf(today)).query(Long.class).single();
        long live = jdbc.sql("select count(distinct event_id) from artist_events where artist_id in (:ids) and live"
                        + " and event_id not in (" + LEFT_OUT + ")")
                .param("ids", artistIds).query(Long.class).single();
        List<Count> byCountry = counts("select coalesce(nullif(e.country, ''), '?') as k, count(*) as n from (" + events
                + ") e group by k order by n desc, k limit 10", artistIds);
        List<Count> byYear = counts("select cast(extract(year from e.date_start) as varchar) as k, count(*) as n from ("
                + events + ") e where e.date_start is not null group by k order by k", artistIds);
        List<Count> byGenre = counts("""
                select eg.genre_id as k, count(distinct eg.event_id) as n
                from event_genres eg
                where eg.event_id in (select ae.event_id from artist_events ae where ae.artist_id in (:ids))
                  and eg.event_id not in (%s)
                group by k order by n desc, k limit 8
                """.formatted(LEFT_OUT), artistIds);
        return new Gigs(total, upcoming, total - upcoming, live, byCountry, byGenre, byYear, since);
    }

    // The crew's next gigs, soonest first, with who of the crew plays (the label's tour on the map)
    /** Every label of the map with its crew size, its crew's upcoming gigs (each event once) and its followers */
    public List<LabelSummary> labels() {
        LocalDate today = LocalDate.now(clock);
        Map<String, Long> followers = new HashMap<>();
        jdbc.sql("select label_name, count(*) as n from label_follows group by label_name")
                .query(rs -> { followers.put(rs.getString("label_name"), rs.getLong("n")); });
        List<LabelSummary> labels = new ArrayList<>();
        for (String label : mapLabels.names()) {
            List<String> slugs = mapLabels.artists(label).orElse(List.of()).stream().map(ArtistNames::slug).toList();
            long upcoming = slugs.isEmpty() ? 0 : jdbc.sql("""
                            select count(distinct s.event_id) from artists a
                            join artist_events ae on ae.artist_id = a.id
                            join event_summaries s on s.event_id = ae.event_id
                            where a.slug in (:slugs) and coalesce(s.date_end, s.date_start) >= :today
                              and s.event_id not in (%s)
                            """.formatted(LEFT_OUT))
                    .param("slugs", slugs).param("today", Date.valueOf(today)).query(Long.class).single();
            labels.add(new LabelSummary(label, slugs.size(), upcoming, followers.getOrDefault(label, 0L)));
        }
        return labels;
    }

    // The crew's gigs, each event once: the next ones soonest first, or the past ones newest first
    private List<LabelGig> crewGigs(Collection<Long> artistIds, boolean past) {
        if (artistIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        select s.event_id, s.name, s.date_start, s.town, s.country, string_agg(a.name, '|' order by a.name) as artists
                        from artist_events ae
                        join event_summaries s on s.event_id = ae.event_id
                        join artists a on a.id = ae.artist_id
                        where ae.artist_id in (:ids) and (coalesce(s.date_end, s.date_start) >= :today) <> :past
                          and s.event_id not in (%s)
                        group by s.event_id, s.name, s.date_start, s.town, s.country
                        order by %s
                        limit 40
                        """.formatted(LEFT_OUT, past ? "s.date_start desc, s.event_id" : "s.date_start, s.event_id"))
                .param("ids", artistIds).param("today", Date.valueOf(LocalDate.now(clock))).param("past", past)
                .query((rs, row) -> new LabelGig(rs.getLong("event_id"), rs.getString("name"),
                        rs.getDate("date_start") == null ? null : rs.getDate("date_start").toLocalDate(),
                        rs.getString("town"), rs.getString("country"), List.of(rs.getString("artists").split("\\|"))))
                .list();
    }

    private List<Count> counts(String sql, Collection<Long> artistIds) {
        return jdbc.sql(sql).param("ids", artistIds)
                .query((rs, row) -> new Count(rs.getString("k"), rs.getLong("n"))).list();
    }

    private long count(String sql, String key) {
        return jdbc.sql(sql).param("key", key).query(Long.class).single();
    }

    // Follows per month, with the running total
    private List<Month> months(String sql, String key) {
        List<Month> months = new ArrayList<>();
        long total = 0;
        for (Count count : jdbc.sql("select to_char(f.created_at, 'YYYY-MM') as k, count(*) as n from (" + sql
                        + ") f group by k order by k").param("key", key)
                .query((rs, row) -> new Count(rs.getString("k"), rs.getLong("n"))).list()) {
            total += count.count();
            months.add(new Month(count.key(), count.count(), total));
        }
        return months;
    }
}
