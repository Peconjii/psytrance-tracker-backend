package com.psytrance.psytrance_tracker_backend.service.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Counts clicks on an event's ticket button (ours: "Tickets ↗", Goabase's: "Goabase ↗"), per event and day. For the
 * admin only: a number to bring to future ticket partnerships, never shown to organizers or the public.
 */
@Service
public class TicketClicks {

    /** Goabase ids and ours are digits; anything else is not an event of this site */
    private static final Pattern EVENT_ID = Pattern.compile("[0-9]{1,19}");
    private static final int NAME_MAX = 200;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TicketClicks(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** One more click today; the name is kept so the admin list can show it after the event left Goabase */
    public void count(String eventId, String eventName) {
        if (eventId == null || !EVENT_ID.matcher(eventId).matches()) {
            throw new IllegalArgumentException("Not an event id: " + eventId);
        }
        String name = eventName == null || eventName.isBlank() ? null
                : eventName.strip().substring(0, Math.min(eventName.strip().length(), NAME_MAX));
        jdbc.update("""
                insert into ticket_clicks (event_id, day, event_name, clicks) values (?, ?, ?, 1)
                on conflict (event_id, day) do update
                set clicks = ticket_clicks.clicks + 1, event_name = coalesce(excluded.event_name, ticket_clicks.event_name)
                """, eventId, LocalDate.now(clock), name);
    }

    public record EventClicks(String eventId, String eventName, long clicks, long today) {
    }

    /** The events clicked most in the last {@code days} days, most first */
    public List<EventClicks> top(int days, int limit) {
        LocalDate today = LocalDate.now(clock);
        return jdbc.query("""
                select event_id, max(event_name) as event_name, sum(clicks) as clicks,
                       sum(case when day = ? then clicks else 0 end) as today
                from ticket_clicks where day > ?
                group by event_id order by clicks desc, event_id limit ?
                """, (rs, i) -> new EventClicks(rs.getString("event_id"), rs.getString("event_name"),
                        rs.getLong("clicks"), rs.getLong("today")),
                today, today.minusDays(days), limit);
    }
}
