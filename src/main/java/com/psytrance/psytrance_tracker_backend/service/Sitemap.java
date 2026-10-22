package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The sitemap for search engines (the frontend's /sitemap.xml is served from here): the main pages, every artist on the
 * map or with gigs coming up, organizers with events coming up, labels, festivals and upcoming events. Pages for
 * logged-in people and the admin side are not in it.
 */
@Service
public class Sitemap {

    // A sitemap holds 50 000 addresses at most; far more than the site has
    private static final int MAX_URLS = 50_000;

    private final JdbcClient jdbc;
    private final MapPlacements mapPlacements;
    private final MapLabels mapLabels;
    private final MapFestivals mapFestivals;
    private final Clock clock;
    private final String site;

    public Sitemap(JdbcClient jdbc, MapPlacements mapPlacements, MapLabels mapLabels, MapFestivals mapFestivals, Clock clock,
                   @Value("${app.frontend-url}") String frontendUrl) {
        this.jdbc = jdbc;
        this.mapPlacements = mapPlacements;
        this.mapLabels = mapLabels;
        this.mapFestivals = mapFestivals;
        this.clock = clock;
        this.site = frontendUrl.replaceAll("/+$", "");
    }

    public String xml() {
        Date today = Date.valueOf(LocalDate.now(clock));
        Set<String> paths = new LinkedHashSet<>();
        paths.add("/");
        paths.add("/events");
        paths.add("/map");
        paths.add("/artists");
        paths.add("/organizers");

        // Artists: the map's (they always have a page) and the ones with gigs coming up
        Set<String> artists = new TreeSet<>();
        mapPlacements.placements().values().forEach(names -> names.forEach(name -> artists.add(ArtistNames.slug(name))));
        artists.addAll(jdbc.sql("""
                        select distinct a.slug from artists a
                        join artist_events ae on ae.artist_id = a.id
                        join event_summaries s on s.event_id = ae.event_id
                        where coalesce(s.date_end, s.date_start) >= :today
                          and ae.event_id not in (%s)
                          and a.slug not in (select slug from artist_blocklist)
                        """.formatted(Stats.LEFT_OUT))
                .param("today", today).query(String.class).list());
        add(paths, "/artists/", artists);

        add(paths, "/organizers/", jdbc.sql("""
                        select distinct s.organizer_slug from event_summaries s
                        where s.organizer_slug is not null and coalesce(s.date_end, s.date_start) >= :today
                        order by s.organizer_slug
                        """)
                .param("today", today).query(String.class).list());
        add(paths, "/labels/", new TreeSet<>(mapLabels.names()));
        add(paths, "/festivals/", new TreeSet<>(mapFestivals.all().keySet()));
        add(paths, "/events/", jdbc.sql("""
                        select s.event_id from event_summaries s
                        where coalesce(s.date_end, s.date_start) >= :today and s.event_id not in (%s)
                        order by s.date_start
                        """.formatted(Stats.LEFT_OUT))
                .param("today", today).query(Long.class).list().stream().map(String::valueOf).toList());

        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        paths.stream().limit(MAX_URLS).forEach(path ->
                xml.append("  <url><loc>").append(escape(site + path)).append("</loc></url>\n"));
        return xml.append("</urlset>\n").toString();
    }

    private static void add(Set<String> paths, String prefix, Collection<String> keys) {
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                paths.add(prefix + URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
    }

    private static String escape(String url) {
        return url.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
