package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A user follows an artist: their gigs glow on the map, and new gigs are emailed. */
@Entity
@Table(name = "artist_follows")
public class ArtistFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "artist_slug", nullable = false, length = 100)
    private String artistSlug;

    @Column(name = "artist_name", nullable = false, length = 100)
    private String artistName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ArtistFollow() {
    }

    public ArtistFollow(User user, String artistSlug, String artistName, Instant createdAt) {
        this.user = user;
        this.artistSlug = artistSlug;
        this.artistName = artistName;
        this.createdAt = createdAt;
    }

    public User getUser() {
        return user;
    }

    public String getArtistSlug() {
        return artistSlug;
    }

    public String getArtistName() {
        return artistName;
    }
}
