package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.OurEvent;
import com.psytrance.psytrance_tracker_backend.service.Stats.Count;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Numbers for a festival of the map. The map has no editions, so they come from what the site keeps: the events whose
 * name has the festival's name as a whole word ("Ozora Festival 2026" for "ozora"), with their line-ups, genres,
 * "I'm going" and saves, and the years artists wrote on their own profiles that they played it.
 */
@Service
public class FestivalStats {

    private final JdbcClient jdbc;
    private final Clock clock;

    public FestivalStats(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** linked: an admin or editor set it as an edition by hand (else its name matched) */
    public record Edition(long eventId, String name, LocalDate dateStart, String town, String country, boolean upcoming,
                          long artists, long going, long saved, boolean linked) {
    }

    public record Numbers(String match, List<Edition> editions, List<Count> byGenre,
                        List<Count> topArtists,
                        List<Count> reportedByYear) {
    }

    /**
     * The core of a festival's name, to find its editions among the events (the festival page does the same):
     * "Hadra Trance Festival" -> "hadra", "MoDem (Momento Demento)" -> "modem", "Universo Paralello" stays
     */
    public static String matchOf(String name) {
        return name.replaceAll("\\(.*?\\)", "").replaceAll("(?i)\\b(trance festival|festival|open air)\\b", "")
                .replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    public Numbers festival(String match) {
        return festival(match, null);
    }

    /**
     * The events named like the festival ({@code match}), plus the ones set as its editions by hand and minus the ones
     * set as not its editions, when {@code festivalId} is given.
     */
    public Numbers festival(String match, String festivalId) {
        String term = match == null ? "" : match.strip().toLowerCase(Locale.ROOT);
        if (term.length() < 3 || term.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The festival name to look for is 3 to 60 letters");
        }
        // The name as a whole word, so "boom" finds "Boom Festival 2026" but not "Boomshankar"
        Pattern word = Pattern.compile("(?iu)(^|[^\\p{L}\\p{N}])" + Pattern.quote(term) + "($|[^\\p{L}\\p{N}])");
        LocalDate today = LocalDate.now(clock);
        Map<Long, Boolean> byHand = festivalId == null ? Map.of() : jdbc.sql("""
                        select event_id, edition from festival_editions where festival_id = :festival
                        """).param("festival", festivalId)
                .query((rs, row) -> Map.entry(rs.getLong(1), rs.getBoolean(2))).list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<Long> linked = byHand.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
        List<Edition> editions = jdbc.sql("""
                        select s.event_id, s.name, s.date_start, s.town, s.country, coalesce(s.date_end, s.date_start) as last_day,
                               (select count(*) from artist_events ae where ae.event_id = s.event_id) as artists,
                               (select count(*) from attendances a where a.event_id = s.event_id) as going,
                               (select count(*) from favorites f where f.event_id = cast(s.event_id as varchar)) as saved
                        from event_summaries s
                        where (lower(s.name) like :like or s.event_id in (:linked))
                          and s.event_id not in (select event_id from hidden_events)
                          and s.event_id not in (select %d + id from our_events where status <> '%s')
                        order by s.date_start desc nulls last
                        limit 60
                        """.formatted(OurEvent.ID_BASE, OurEvent.APPROVED))
                .param("like", "%" + term + "%")
                // -1 keeps "in ()" valid when nothing is linked
                .param("linked", linked.isEmpty() ? List.of(-1L) : linked)
                .query((rs, row) -> {
                    Date start = rs.getDate("date_start");
                    Date last = rs.getDate("last_day");
                    long eventId = rs.getLong("event_id");
                    return new Edition(eventId, rs.getString("name"), start == null ? null : start.toLocalDate(),
                            rs.getString("town"), rs.getString("country"), last != null && !last.toLocalDate().isBefore(today),
                            rs.getLong("artists"), rs.getLong("going"), rs.getLong("saved"), byHand.getOrDefault(eventId, false));
                })
                .list().stream()
                .filter(edition -> byHand.containsKey(edition.eventId())
                        ? byHand.get(edition.eventId())
                        : word.matcher(edition.name()).find())
                .limit(50)
                .toList();
        List<Long> ids = editions.stream().map(Edition::eventId).toList();
        List<Count> byGenre = ids.isEmpty() ? List.of() : counts("""
                select genre_id as k, count(distinct event_id) as n from event_genres where event_id in (:ids)
                group by k order by n desc, k limit 8
                """, ids);
        List<Count> topArtists = ids.isEmpty() ? List.of() : counts("""
                select a.name as k, count(distinct ae.event_id) as n from artist_events ae join artists a on a.id = ae.artist_id
                where ae.event_id in (:ids) group by a.name order by n desc, a.name limit 20
                """, ids);
        List<Count> reported = jdbc.sql("""
                        select cast(year as varchar) as k, count(distinct artist_slug) as n from artist_festivals
                        where lower(name) like :like and year is not null group by year order by year
                        """)
                .param("like", "%" + term + "%")
                .query((rs, row) -> new Count(rs.getString("k"), rs.getLong("n")))
                .list();
        return new Numbers(term, editions, byGenre, topArtists, reported);
    }

    private List<Count> counts(String sql, List<Long> ids) {
        return jdbc.sql(sql).param("ids", ids)
                .query((rs, row) -> new Count(rs.getString("k"), rs.getLong("n")))
                .list();
    }
}
