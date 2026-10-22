package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The weekly email, on Mondays: where the artists an account follows, and the crews of the labels it follows, play in
 * the next two weeks. Only to confirmed emails, only when there is something to tell, at most once a week (the job runs
 * every hour, and a sleeping server may miss some runs).
 */
@Service
public class WeeklyDigest {

    static final int DAYS_AHEAD = 14;
    private static final int MAX_GIGS = 30;

    private final JdbcClient jdbc;
    private final MapLabels mapLabels;
    private final DigestMailer mailer;
    private final Clock clock;

    public WeeklyDigest(JdbcClient jdbc, MapLabels mapLabels, DigestMailer mailer, Clock clock) {
        this.jdbc = jdbc;
        this.mapLabels = mapLabels;
        this.mailer = mailer;
        this.clock = clock;
    }

    /** One upcoming event and who of the followed plays there */
    public record Gig(long eventId, String name, LocalDate dateStart, String town, String country, List<String> artists) {
    }

    record Reader(long id, String username, String email) {
    }

    /** On Mondays: sends the week's emails that are due. Returns how many were sent. */
    public int sendDue() {
        LocalDate today = LocalDate.now(clock);
        return today.getDayOfWeek() == DayOfWeek.MONDAY ? send(today) : 0;
    }

    /** Sends the emails due on {@code today} (each account once a day at most); returns how many were sent */
    public int send(LocalDate today) {
        List<Reader> readers = jdbc.sql("""
                        select u.id, u.username, u.email from app_user u
                        where u.weekly_digest and u.email_verified and u.email is not null
                          and (u.weekly_digest_sent_on is null or u.weekly_digest_sent_on < :today)
                          and (exists (select 1 from artist_follows f where f.user_id = u.id)
                               or exists (select 1 from label_follows l where l.user_id = u.id))
                        """)
                .param("today", Date.valueOf(today))
                .query((rs, row) -> new Reader(rs.getLong("id"), rs.getString("username"), rs.getString("email")))
                .list();
        int sent = 0;
        for (Reader reader : readers) {
            List<Gig> gigs = gigsFor(reader.id(), today);
            // Nothing to tell is done for this week too; a failed mail is tried again on the next run
            boolean done = gigs.isEmpty() || mailer.send(reader.username(), reader.email(), gigs);
            if (done) {
                jdbc.sql("update app_user set weekly_digest_sent_on = :today where id = :id")
                        .param("today", Date.valueOf(today)).param("id", reader.id()).update();
                if (!gigs.isEmpty()) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /** The next two weeks of the artists this account follows, and of the crews of its labels */
    List<Gig> gigsFor(long userId, LocalDate today) {
        Set<String> slugs = new TreeSet<>(jdbc.sql("select artist_slug from artist_follows where user_id = :user")
                .param("user", userId).query(String.class).list());
        for (String label : jdbc.sql("select label_name from label_follows where user_id = :user")
                .param("user", userId).query(String.class).list()) {
            mapLabels.artists(label).ifPresent(crew -> crew.forEach(name -> slugs.add(ArtistNames.slug(name))));
        }
        if (slugs.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        select s.event_id, s.name, s.date_start, s.town, s.country,
                               string_agg(distinct a.name, '|' order by a.name) as artists
                        from event_summaries s
                        join artist_events ae on ae.event_id = s.event_id
                        join artists a on a.id = ae.artist_id
                        where a.slug in (:slugs)
                          and s.date_start between :today and :until
                          and s.event_id not in (%s)
                        group by s.event_id, s.name, s.date_start, s.town, s.country
                        order by s.date_start, s.name
                        limit :max
                        """.formatted(Stats.LEFT_OUT))
                .param("slugs", slugs).param("today", Date.valueOf(today))
                .param("until", Date.valueOf(today.plusDays(DAYS_AHEAD))).param("max", MAX_GIGS)
                .query((rs, row) -> new Gig(rs.getLong("event_id"), rs.getString("name"),
                        rs.getDate("date_start").toLocalDate(), rs.getString("town"), rs.getString("country"),
                        Arrays.asList(rs.getString("artists").split("\\|"))))
                .list();
    }
}
