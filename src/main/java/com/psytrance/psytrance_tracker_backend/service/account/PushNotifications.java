package com.psytrance.psytrance_tracker_backend.service.account;

import com.psytrance.psytrance_tracker_backend.client.FcmClient;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

/**
 * The phones (Android app) of each account, and the push notifications they get:
 * a timetable coming out for an event they're going to, and someone joining their crew.
 * Sending runs in the background, so nobody's click waits for Google.
 */
@Service
public class PushNotifications {

    static final int MAX_DEVICES = 10;

    private final JdbcClient jdbc;
    private final FcmClient fcm;
    private final Clock clock;

    public PushNotifications(JdbcClient jdbc, FcmClient fcm, Clock clock) {
        this.jdbc = jdbc;
        this.fcm = fcm;
        this.clock = clock;
    }

    /** The app on this phone, for this account (a token another account had moves over: one phone, one account) */
    public void register(String username, String token) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.sql("""
                        insert into push_device (user_id, token, created_at, seen_at)
                        select id, :t, :now, :now from app_user where username = :u
                        on conflict (token) do update set user_id = excluded.user_id, seen_at = excluded.seen_at""")
                .param("t", token).param("u", username).param("now", now).update();
        // An account keeps its newest phones only
        jdbc.sql("""
                        delete from push_device where id in (
                            select d.id from push_device d join app_user u on u.id = d.user_id
                            where u.username = :u order by d.seen_at desc offset :max)""")
                .param("u", username).param("max", MAX_DEVICES).update();
    }

    /** Logged out on this phone: it gets nothing more for this account */
    public void unregister(String username, String token) {
        jdbc.sql("delete from push_device where token = :t and user_id = (select id from app_user where username = :u)")
                .param("t", token).param("u", username).update();
    }

    @Async
    public void timetableOut(long eventId) {
        record Going(String token, String eventName) {
        }
        List<Going> going = jdbc.sql("""
                        select d.token, a.event_name from push_device d join attendances a on a.user_id = d.user_id
                        where a.event_id = :e""")
                .param("e", eventId).query(Going.class).list();
        for (Going g : going) {
            send(List.of(g.token()), g.eventName() == null ? "Map of Psy" : g.eventName(),
                    "The timetable is out: see who plays when", "/events/" + eventId);
        }
    }

    @Async
    public void joinedCrew(long crewId, long newMemberId, String newMemberName, String crewName, String code) {
        List<String> tokens = jdbc.sql("""
                        select d.token from push_device d join crew_member m on m.user_id = d.user_id
                        where m.crew_id = :c and m.user_id <> :me""")
                .param("c", crewId).param("me", newMemberId).query(String.class).list();
        send(tokens, crewName, newMemberName + " joined your crew", "/crew/" + code);
    }

    private void send(List<String> tokens, String title, String body, String path) {
        if (!fcm.isEnabled()) {
            return;
        }
        for (String token : tokens) {
            if (fcm.send(token, title, body, path) == FcmClient.Result.TOKEN_GONE) {
                jdbc.sql("delete from push_device where token = :t").param("t", token).update();
            }
        }
    }
}
