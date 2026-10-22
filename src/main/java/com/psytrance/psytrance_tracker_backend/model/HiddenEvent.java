package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An event an admin hid from the site (not psytrance, a duplicate...); the name is kept for the admin's list */
@Entity
@Table(name = "hidden_events")
public class HiddenEvent {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(length = 300)
    private String reason;

    @Column(name = "hidden_by", nullable = false)
    private String hiddenBy;

    @Column(name = "hidden_at", nullable = false)
    private Instant hiddenAt;

    protected HiddenEvent() {
    }

    public HiddenEvent(long eventId, String name, String reason, String hiddenBy, Instant hiddenAt) {
        this.eventId = eventId;
        this.name = name;
        this.reason = reason;
        this.hiddenBy = hiddenBy;
        this.hiddenAt = hiddenAt;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public String getReason() {
        return reason;
    }

    public String getHiddenBy() {
        return hiddenBy;
    }

    public Instant getHiddenAt() {
        return hiddenAt;
    }
}
