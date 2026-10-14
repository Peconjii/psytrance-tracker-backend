package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

/** An artist playing at an event (one row per artist and event). */
@Entity
@Table(name = "artist_events")
public class ArtistEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    protected ArtistEvent() {
    }

    public ArtistEvent(Artist artist, Long eventId) {
        this.artist = artist;
        this.eventId = eventId;
    }

    public Long getId() {
        return id;
    }

    public Artist getArtist() {
        return artist;
    }

    public Long getEventId() {
        return eventId;
    }
}
