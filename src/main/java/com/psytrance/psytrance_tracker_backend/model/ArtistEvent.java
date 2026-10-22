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

    // The line-up wrote "live" next to the artist
    @Column(name = "live", nullable = false)
    private boolean live;

    protected ArtistEvent() {
    }

    public ArtistEvent(Artist artist, Long eventId) {
        this(artist, eventId, false);
    }

    public ArtistEvent(Artist artist, Long eventId, boolean live) {
        this.artist = artist;
        this.eventId = eventId;
        this.live = live;
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
