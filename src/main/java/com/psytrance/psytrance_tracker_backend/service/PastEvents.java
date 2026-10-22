package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.PageResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The archive: events that are over, newest first. Goabase lists only what is coming up, so this is what the site
 * kept itself (event_summaries, since October 2026), with how many artists played and how many people were going.
 */
@Service
public class PastEvents {

    private final JdbcClient jdbc;
    private final Clock clock;

    public PastEvents(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record PastEvent(long id, String name, LocalDate dateStart, LocalDate dateEnd, String town, String country,
                            long artists, long going) {
    }

    /** search: in the name or the town, like the upcoming list */
    public PageResponse<PastEvent> find(String search, int page, int size) {
        String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        String like = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Date today = Date.valueOf(LocalDate.now(clock));
        String where = """
                where coalesce(s.date_end, s.date_start) < :today
                  and s.event_id not in (%s)
                  and (:all or lower(s.name) like :like escape '\\' or lower(coalesce(s.town, '')) like :like escape '\\')
                """.formatted(Stats.LEFT_OUT);
        long total = jdbc.sql("select count(*) from event_summaries s " + where)
                .param("today", today).param("all", term.isEmpty()).param("like", like)
                .query(Long.class).single();
        List<PastEvent> events = jdbc.sql("""
                        select s.event_id, s.name, s.date_start, s.date_end, s.town, s.country,
                               (select count(*) from artist_events ae where ae.event_id = s.event_id) as artists,
                               (select count(*) from attendances a where a.event_id = s.event_id) as going
                        from event_summaries s
                        """ + where + """
                        order by s.date_start desc nulls last, s.name
                        limit :size offset :offset
                        """)
                .param("today", today).param("all", term.isEmpty()).param("like", like)
                .param("size", size).param("offset", (long) page * size)
                .query((rs, row) -> {
                    Date start = rs.getDate("date_start");
                    Date end = rs.getDate("date_end");
                    return new PastEvent(rs.getLong("event_id"), rs.getString("name"),
                            start == null ? null : start.toLocalDate(), end == null ? null : end.toLocalDate(),
                            rs.getString("town"), rs.getString("country"), rs.getLong("artists"), rs.getLong("going"));
                })
                .list();
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(events, page, size, total, totalPages, (long) (page + 1) * size < total);
    }
}
