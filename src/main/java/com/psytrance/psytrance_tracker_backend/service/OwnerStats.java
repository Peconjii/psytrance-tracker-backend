package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The numbers only an artist's or organizer's owner (and admins) see: per gig or event how many people are going, saved
 * it, reviewed it and how it was rated, and how many people heard of it through following. The public numbers are in
 * {@link Stats}.
 */
@Service
public class OwnerStats {

    private final JdbcClient jdbc;
    private final ArtistRepository artistRepository;
    private final ArtistEditing artistEditing;
    private final OrganizerEditing organizerEditing;
    private final LabelPages labelPages;
    private final FestivalPages festivalPages;
    private final FestivalStats festivalStats;
    private final MapLabels mapLabels;
    private final MapFestivals mapFestivals;
    private final Clock clock;

    // People who follow someone in the event's line-up
    private static final String LINE_UP_REACH = """
            (select count(distinct f.user_id) from artist_follows f
               join artists a on a.slug = f.artist_slug
               join artist_events ae on ae.artist_id = a.id
             where ae.event_id = s.event_id)
            """;

    public OwnerStats(JdbcClient jdbc, ArtistRepository artistRepository, ArtistEditing artistEditing,
                      OrganizerEditing organizerEditing, LabelPages labelPages, FestivalPages festivalPages,
                      FestivalStats festivalStats, MapLabels mapLabels, MapFestivals mapFestivals, Clock clock) {
        this.jdbc = jdbc;
        this.artistRepository = artistRepository;
        this.artistEditing = artistEditing;
        this.organizerEditing = organizerEditing;
        this.labelPages = labelPages;
        this.festivalPages = festivalPages;
        this.festivalStats = festivalStats;
        this.mapLabels = mapLabels;
        this.mapFestivals = mapFestivals;
        this.clock = clock;
    }

    /**
     * One gig or event: {@code reach} is, for an artist, the followers who heard of this gig (listed when they
     * followed, or emailed when it was new); for a label, the label's followers who heard of it; for an organizer or a
     * festival, the people who follow someone in its line-up.
     */
    public record EventNumbers(long eventId, String name, LocalDate dateStart, String town, String country,
                               boolean upcoming, long going, long saved, long reviews, Double rating, long reach) {
    }

    public record Totals(long going, long saved, long reviews, long reach) {
    }

    /** {@code followersWithEmails}: followers who get emails from us (new gigs reach them by email) */
    public record ArtistNumbers(String slug, long followers, long followersWithEmails, Totals totals, List<EventNumbers> gigs) {
    }

    public record OrganizerNumbers(String slug, Totals totals, List<EventNumbers> events) {
    }

    /** A label's crew gigs; {@code followers}: the label's own followers */
    public record LabelNumbers(String label, long followers, Totals totals, List<EventNumbers> gigs) {
    }

    /** A festival's editions (events with its name as a word, see FestivalStats) */
    public record FestivalNumbers(String id, Totals totals, List<EventNumbers> editions) {
    }

    public ArtistNumbers artist(String slug, String username) {
        Artist artist = artistRepository.findBySlug(ArtistNames.slug(slug))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No artist called " + slug));
        requireOwner(artistEditing.canEdit(artist.getSlug(), username), username);
        List<EventNumbers> gigs = events("""
                        (select count(*) from follow_notices n where n.event_id = s.event_id
                           and n.user_id in (select f.user_id from artist_follows f where f.artist_slug = :slug))
                        """, "s.event_id in (select ae.event_id from artist_events ae where ae.artist_id = :id)",
                Map.of("slug", artist.getSlug(), "id", artist.getId()));
        long followers = jdbc.sql("select count(*) from artist_follows where artist_slug = :slug")
                .param("slug", artist.getSlug()).query(Long.class).single();
        long withEmails = jdbc.sql("""
                        select count(*) from artist_follows f join app_user u on u.id = f.user_id
                        where f.artist_slug = :slug and u.event_reminders and u.email is not null and u.email <> ''
                        """).param("slug", artist.getSlug()).query(Long.class).single();
        return new ArtistNumbers(artist.getSlug(), followers, withEmails, totals(gigs), gigs);
    }

    public OrganizerNumbers organizer(String slug, String username) {
        String clean = ArtistNames.slug(slug);
        requireOwner(organizerEditing.canEdit(clean, username), username);
        List<EventNumbers> events = events(LINE_UP_REACH, "s.organizer_slug = :slug", Map.of("slug", clean));
        return new OrganizerNumbers(clean, totals(events), events);
    }

    public LabelNumbers label(String label, String username) {
        List<String> crew = mapLabels.artists(label)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No label called " + label));
        requireOwner(labelPages.canEdit(label, username), username);
        List<String> slugs = crew.stream().map(ArtistNames::slug).toList();
        List<EventNumbers> gigs = events("""
                        (select count(*) from follow_notices n where n.event_id = s.event_id
                           and n.user_id in (select f.user_id from label_follows f where f.label_name = :label))
                        """, "s.event_id in (select ae.event_id from artist_events ae join artists a on a.id = ae.artist_id where a.slug in (:slugs))",
                Map.of("label", label, "slugs", slugs.isEmpty() ? List.of("") : slugs));
        long followers = jdbc.sql("select count(*) from label_follows where label_name = :label")
                .param("label", label).query(Long.class).single();
        return new LabelNumbers(label, followers, totals(gigs), gigs);
    }

    public FestivalNumbers festival(String id, String username) {
        String name = mapFestivals.name(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No festival called " + id));
        requireOwner(festivalPages.canEdit(id, username), username);
        List<Long> ids = festivalStats.festival(FestivalStats.matchOf(name)).editions().stream()
                .map(FestivalStats.Edition::eventId).toList();
        List<EventNumbers> editions = ids.isEmpty() ? List.of()
                : events(LINE_UP_REACH, "s.event_id in (:ids)", Map.of("ids", ids));
        return new FestivalNumbers(id, totals(editions), editions);
    }

    // The events matching `which`, newest first, each with its numbers; `reach` is the SQL for the reach column
    private List<EventNumbers> events(String reach, String which, Map<String, Object> params) {
        LocalDate today = LocalDate.now(clock);
        return jdbc.sql("""
                        select s.event_id, s.name, s.date_start, s.town, s.country, coalesce(s.date_end, s.date_start) as last_day,
                               (select count(*) from attendances a where a.event_id = s.event_id) as going,
                               (select count(*) from favorites f where f.event_id = cast(s.event_id as varchar)) as saved,
                               (select count(*) from reviews r where r.event_id = cast(s.event_id as varchar)) as reviews,
                               (select avg(r.rating) from reviews r where r.event_id = cast(s.event_id as varchar)) as rating,
                               %s as reach
                        from event_summaries s
                        where %s and s.event_id not in (%s)
                        order by s.date_start desc nulls last
                        limit 60
                        """.formatted(reach, which, Stats.LEFT_OUT))
                .params(params)
                .query((rs, row) -> {
                    Date start = rs.getDate("date_start");
                    Date last = rs.getDate("last_day");
                    BigDecimal rating = rs.getBigDecimal("rating");
                    return new EventNumbers(rs.getLong("event_id"), rs.getString("name"),
                            start == null ? null : start.toLocalDate(), rs.getString("town"), rs.getString("country"),
                            last != null && !last.toLocalDate().isBefore(today), rs.getLong("going"), rs.getLong("saved"),
                            rs.getLong("reviews"), rating == null ? null : Math.round(rating.doubleValue() * 10) / 10.0, rs.getLong("reach"));
                })
                .list();
    }

    private static Totals totals(List<EventNumbers> events) {
        return new Totals(events.stream().mapToLong(EventNumbers::going).sum(),
                events.stream().mapToLong(EventNumbers::saved).sum(),
                events.stream().mapToLong(EventNumbers::reviews).sum(),
                events.stream().mapToLong(EventNumbers::reach).sum());
    }

    private static void requireOwner(boolean owner, String username) {
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first");
        }
        if (!owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner and admins see these numbers");
        }
    }
}
