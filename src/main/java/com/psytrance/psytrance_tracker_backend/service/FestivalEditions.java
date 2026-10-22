package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * An admin or editor sets which events are a festival's editions when the name doesn't say it: "O.Z.O.R.A. One Day"
 * is Ozora's, "Boomshankar Night" is not Boom's. Read by FestivalStats; every change is in the history.
 */
@Service
public class FestivalEditions {

    private final JdbcClient jdbc;
    private final MapFestivals mapFestivals;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public FestivalEditions(JdbcClient jdbc, MapFestivals mapFestivals, AdminChangeRepository changeRepository, Clock clock) {
        this.jdbc = jdbc;
        this.mapFestivals = mapFestivals;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    /** edition: true (it is one), false (it isn't, whatever its name says) or null (back to going by the name) */
    @Transactional
    public void set(String festivalId, long eventId, Boolean edition, String username) {
        String festival = mapFestivals.name(festivalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No festival " + festivalId + " on the map"));
        String event = jdbc.sql("select name from event_summaries where event_id = :id").param("id", eventId)
                .query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No event " + eventId + " in the lists"));
        String before = jdbc.sql("select edition from festival_editions where festival_id = :festival and event_id = :event")
                .param("festival", festivalId).param("event", eventId).query(Boolean.class).optional()
                .map(String::valueOf).orElse("");
        jdbc.sql("delete from festival_editions where festival_id = :festival and event_id = :event")
                .param("festival", festivalId).param("event", eventId).update();
        if (edition != null) {
            jdbc.sql("""
                            insert into festival_editions (festival_id, event_id, edition, decided_by, decided_at)
                            values (:festival, :event, :edition, :by, :at)
                            """)
                    .param("festival", festivalId).param("event", eventId).param("edition", edition)
                    .param("by", username).param("at", Timestamp.from(clock.instant())).update();
        }
        String target = festival + ": " + event;
        changeRepository.save(new AdminChange(username, AdminChange.FESTIVAL_EDITION, target.length() > 200 ? target.substring(0, 200) : target,
                before, edition == null ? "" : String.valueOf(edition), clock.instant()));
    }
}
