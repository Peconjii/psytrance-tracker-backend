package com.psytrance.psytrance_tracker_backend.model;

import com.psytrance.psytrance_tracker_backend.dto.GenreShare;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One Map of Psy genre of an event and how big a part of the event it is (see EventGenres). */
@Entity
@Table(name = "event_genres")
public class EventGenre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "genre_id", nullable = false, length = 30)
    private String genreId;

    @Column(name = "share", nullable = false)
    private float share;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private GenreShare.Source source;

    protected EventGenre() {
    }

    public EventGenre(Long eventId, GenreShare genre) {
        this.eventId = eventId;
        this.genreId = genre.genreId();
        this.share = (float) genre.share();
        this.source = genre.source();
    }

    public Long getEventId() {
        return eventId;
    }

    public GenreShare toShare() {
        return new GenreShare(genreId, share, source);
    }
}
