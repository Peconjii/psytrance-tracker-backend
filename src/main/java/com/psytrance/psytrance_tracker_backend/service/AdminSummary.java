package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.MapEventDto;
import com.psytrance.psytrance_tracker_backend.dto.MapPeriod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The admin dashboard: what is waiting for an admin (also the number next to "Admin" in the menu), the accounts, the
 * ships of the next 30 days, how the artist index is doing and the most followed artists.
 */
@Service
public class AdminSummary {

    private final JdbcClient jdbc;
    private final MapService mapService;
    private final ArtistIndexer artistIndexer;
    private final Clock clock;

    public AdminSummary(JdbcClient jdbc, MapService mapService, ArtistIndexer artistIndexer, Clock clock) {
        this.jdbc = jdbc;
        this.mapService = mapService;
        this.artistIndexer = artistIndexer;
        this.clock = clock;
    }

    /** What waits for an admin: new feedback, events added on our site, artists' and labels' requests, page claims */
    public record Waiting(long feedback, long events, long artistRequests, long total) {
        Waiting(long feedback, long events, long artistRequests) {
            this(feedback, events, artistRequests, feedback + events + artistRequests);
        }
    }

    public record Followed(String slug, String name, long followers) {
    }

    public record Dashboard(Waiting waiting, long users, long newUsersThisWeek, long ships, long harbour,
                            ArtistIndexer.Status index, List<Followed> mostFollowed) {
    }

    /** Cheap: three counts, for the menu */
    public Waiting waiting() {
        return new Waiting(
                count("select count(*) from feedback where status = 'NEW'"),
                count("select count(*) from our_events where status = 'PENDING'"),
                count("select (select count(*) from artist_requests where status = 'PENDING')"
                        + " + (select count(*) from label_requests where status = 'PENDING')"
                        + " + (select count(*) from page_claims where status = 'PENDING')"
                        + " + (select count(*) from page_requests where status = 'PENDING')"));
    }

    public Dashboard dashboard() {
        List<MapEventDto> month = mapService.findEvents(MapPeriod.MONTH);
        long harbour = month.stream().filter(event -> event.genres().isEmpty()).count();
        Instant weekAgo = clock.instant().minus(Duration.ofDays(7));
        List<Followed> mostFollowed = jdbc.sql("""
                        select artist_slug, max(artist_name) as name, count(*) as followers
                        from artist_follows group by artist_slug order by followers desc, artist_slug limit 5
                        """)
                .query((rs, row) -> new Followed(rs.getString("artist_slug"), rs.getString("name"), rs.getLong("followers")))
                .list();
        return new Dashboard(waiting(), count("select count(*) from app_user"),
                jdbc.sql("select count(*) from app_user where created_at > :since")
                        .param("since", Timestamp.from(weekAgo)).query(Long.class).single(),
                month.size() - harbour, harbour, artistIndexer.status(), mostFollowed);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
