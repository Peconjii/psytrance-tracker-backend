package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An event an admin features on the map, with an optional short note ("Tickets almost gone") */
@Entity
@Table(name = "featured_events")
public class FeaturedEvent {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(length = 200)
    private String note;

    @Column(name = "featured_by", nullable = false)
    private String featuredBy;

    @Column(name = "featured_at", nullable = false)
    private Instant featuredAt;

    protected FeaturedEvent() {
    }

    public FeaturedEvent(long eventId, String name, String note, String featuredBy, Instant featuredAt) {
        this.eventId = eventId;
        this.name = name;
        this.note = note;
        this.featuredBy = featuredBy;
        this.featuredAt = featuredAt;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public String getNote() {
        return note;
    }

    public String getFeaturedBy() {
        return featuredBy;
    }

    public Instant getFeaturedAt() {
        return featuredAt;
    }
}
