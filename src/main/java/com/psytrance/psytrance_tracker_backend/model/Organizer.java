package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

import static com.psytrance.psytrance_tracker_backend.util.Texts.blankToNull;

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

    // Written by the organizer (their owner) or an admin
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "instagram_url", length = 300)
    private String instagramUrl;

    @Column(name = "website_url", length = 300)
    private String websiteUrl;

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

    /** Replaces the page; blank fields are stored as null */
    public void updateProfile(String photoUrl, String bio, String instagramUrl, String websiteUrl) {
        this.photoUrl = blankToNull(photoUrl);
        this.bio = blankToNull(bio);
        this.instagramUrl = blankToNull(instagramUrl);
        this.websiteUrl = blankToNull(websiteUrl);
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getBio() {
        return bio;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }
}
