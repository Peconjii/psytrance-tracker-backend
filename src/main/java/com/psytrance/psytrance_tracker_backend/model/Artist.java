package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

/** An artist found in at least one event's line-up. */
@Entity
@Table(name = "artists")
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // See ArtistNames.slug: the same artist written differently gets the same slug
    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    // As an organizer wrote it, e.g. "Ace Ventura"
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected Artist() {
    }

    public Artist(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
