package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** The user account that owns an artist's profile, linked by an admin */
@Entity
@Table(name = "artist_owners")
public class ArtistOwner {

    @Id
    @Column(name = "artist_slug", length = 100)
    private String artistSlug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ArtistOwner() {
    }

    public ArtistOwner(String artistSlug, User user, Instant createdAt) {
        this.artistSlug = artistSlug;
        this.user = user;
        this.createdAt = createdAt;
    }

    public String getArtistSlug() {
        return artistSlug;
    }

    public User getUser() {
        return user;
    }
}
