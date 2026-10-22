package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** The user account that owns an organizer's page, linked by an admin */
@Entity
@Table(name = "organizer_owners")
public class OrganizerOwner {

    @Id
    @Column(name = "organizer_slug", length = 100)
    private String organizerSlug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrganizerOwner() {
    }

    public OrganizerOwner(String organizerSlug, User user, Instant createdAt) {
        this.organizerSlug = organizerSlug;
        this.user = user;
        this.createdAt = createdAt;
    }

    public String getOrganizerSlug() {
        return organizerSlug;
    }

    public User getUser() {
        return user;
    }
}
