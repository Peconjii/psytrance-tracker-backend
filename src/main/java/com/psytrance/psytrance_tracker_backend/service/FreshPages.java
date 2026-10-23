package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Artists and organizers who made their own page (or took one over) but have no upcoming event yet. The lists of
 * artists and organizers only know who plays or runs upcoming events, so without this a new page couldn't be found.
 * Newest owner first.
 */
@Service
public class FreshPages {

    private static final int MAX_RESULTS = 50;

    private final JdbcClient jdbc;
    private final EventService eventService;

    public FreshPages(JdbcClient jdbc, EventService eventService) {
        this.jdbc = jdbc;
        this.eventService = eventService;
    }

    /** role: the artist's own pick, or null */
    public record Page(String slug, String name, String photoUrl, String role) {
    }

    public List<Page> artists(String search) {
        return jdbc.sql("""
                        select a.slug, coalesce(p.display_name, a.name), p.photo_url, p.role
                        from artist_owners o
                        join artists a on a.slug = o.artist_slug
                        left join artist_profiles p on p.slug = a.slug
                        where a.slug like :part
                          and not exists (select 1 from artist_events ae where ae.artist_id = a.id and ae.event_id in (:upcoming))
                        order by o.created_at desc
                        limit :max
                        """)
                .param("part", "%" + ArtistNames.slug(search) + "%").param("upcoming", upcomingIds())
                .param("max", MAX_RESULTS)
                .query((rs, row) -> new Page(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .list();
    }

    public List<Page> organizers(String search) {
        return jdbc.sql("""
                        select g.slug, g.name, g.photo_url
                        from organizer_owners o
                        join organizers g on g.slug = o.organizer_slug
                        where g.slug like :part
                          and not exists (select 1 from event_summaries s where s.organizer_slug = g.slug and s.event_id in (:upcoming))
                        order by o.created_at desc
                        limit :max
                        """)
                .param("part", "%" + ArtistNames.slug(search) + "%").param("upcoming", upcomingIds())
                .param("max", MAX_RESULTS)
                .query((rs, row) -> new Page(rs.getString(1), rs.getString(2), rs.getString(3), null))
                .list();
    }

    // Never empty: "in ()" isn't SQL, and no event has the id -1
    private List<Long> upcomingIds() {
        List<Long> ids = eventService.findUpcoming().stream().map(EventDto::id).toList();
        return ids.isEmpty() ? List.of(-1L) : ids;
    }
}
