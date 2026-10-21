package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A line-up word an admin marked as "not an artist" ("Lounge", "Coming Soon"); the artist index skips it. */
@Entity
@Table(name = "artist_blocklist")
public class ArtistBlock {

    @Id
    @Column(name = "slug", length = 100)
    private String slug;

    // As it was written when it was blocked, for the history
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected ArtistBlock() {
    }

    public ArtistBlock(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }
}
