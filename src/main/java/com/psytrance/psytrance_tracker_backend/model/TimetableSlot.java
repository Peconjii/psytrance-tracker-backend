package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One set of an event's timetable: who plays, on which stage, from when (and until when, when known), on the venue clock. */
@Entity
@Table(name = "event_timetable")
public class TimetableSlot {

    /** Read from the line-up, or set by an admin (then the artist index leaves the whole timetable alone) */
    public static final String AUTO = "AUTO";
    public static final String MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "stage", length = 100)
    private String stage;

    @Column(name = "artist_name", nullable = false, length = 100)
    private String artistName;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    // The venue clock's offset from UTC; null when unknown
    @Column(name = "utc_offset_minutes")
    private Integer utcOffsetMinutes;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    protected TimetableSlot() {
    }

    public TimetableSlot(Long eventId, int position, String stage, String artistName,
                         LocalDateTime startsAt, LocalDateTime endsAt, Integer utcOffsetMinutes, String source) {
        this.eventId = eventId;
        this.position = position;
        this.stage = stage;
        this.artistName = artistName;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.utcOffsetMinutes = utcOffsetMinutes;
        this.source = source;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getStage() {
        return stage;
    }

    public String getArtistName() {
        return artistName;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public Integer getUtcOffsetMinutes() {
        return utcOffsetMinutes;
    }

    public String getSource() {
        return source;
    }
}
