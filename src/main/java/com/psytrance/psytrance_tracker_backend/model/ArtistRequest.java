package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A change an artist's owner asks for that an admin decides: other map genres (they move the artist's satellite for
 * everyone) or another name on the page (line-ups still match the old one).
 */
@Entity
@Table(name = "artist_requests")
public class ArtistRequest {

    public enum Kind { GENRES, NAME }

    /** REPLACED: the owner asked again before an admin decided, so only the newest request of a kind is pending */
    public enum Status { PENDING, APPROVED, REJECTED, REPLACED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artist_slug", nullable = false, length = 100)
    private String artistSlug;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private Kind kind;

    /** GENRES: genre ids, comma-separated; NAME: the name */
    @Column(name = "value", nullable = false, length = 500)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ArtistRequest() {
    }

    public ArtistRequest(String artistSlug, Kind kind, String value, User user, Instant createdAt) {
        this.artistSlug = artistSlug;
        this.kind = kind;
        this.value = value;
        this.user = user;
        this.status = Status.PENDING;
        this.createdAt = createdAt;
    }

    public void decide(Status status, String decidedBy, Instant decidedAt) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public String getArtistSlug() {
        return artistSlug;
    }

    public Kind getKind() {
        return kind;
    }

    public String getValue() {
        return value;
    }

    public User getUser() {
        return user;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
