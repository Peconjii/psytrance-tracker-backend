package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** When an event's line-up was last read from Goabase, so the artist index knows what to read next. */
@Entity
@Table(name = "lineup_scans")
public class LineupScan {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    protected LineupScan() {
    }

    public LineupScan(Long eventId, Instant scannedAt) {
        this.eventId = eventId;
        this.scannedAt = scannedAt;
    }

    public Long getEventId() {
        return eventId;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(Instant scannedAt) {
        this.scannedAt = scannedAt;
    }
}
