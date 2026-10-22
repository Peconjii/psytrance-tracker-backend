package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** An organizer of events (a crew, a promoter), known by the name Goabase gives for its events */
@Entity
@Table(name = "organizers")
public class Organizer {

    @Id
    @Column(name = "slug", length = 100)
    private String slug;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    // Their site or page as Goabase has it
    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Organizer() {
    }

    public Organizer(String slug, String name, String url, Instant createdAt) {
        this.slug = slug;
        this.name = name;
        this.url = url;
        this.createdAt = createdAt;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
