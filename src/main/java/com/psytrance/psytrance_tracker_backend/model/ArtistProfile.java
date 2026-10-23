package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

import static com.psytrance.psytrance_tracker_backend.util.Texts.blankToNull;

/** An artist's photo, bio, links and booking address, shown on their page. Written by admins for now. */
@Entity
@Table(name = "artist_profiles")
public class ArtistProfile {

    @Id
    @Column(name = "slug", length = 100)
    private String slug;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "soundcloud_url", length = 300)
    private String soundcloudUrl;

    @Column(name = "bandcamp_url", length = 300)
    private String bandcampUrl;

    @Column(name = "instagram_url", length = 300)
    private String instagramUrl;

    @Column(name = "website_url", length = 300)
    private String websiteUrl;

    @Column(name = "booking_email", length = 255)
    private String bookingEmail;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // The name on the page when it differs from the line-ups' spelling; set when an admin approves a request
    @Column(name = "display_name", length = 100)
    private String displayName;

    // DJ, PRODUCER or LIVE, picked by the artist or an admin; null: not picked (the site guesses)
    @Column(name = "role", length = 20)
    private String role;

    protected ArtistProfile() {
    }

    public ArtistProfile(String slug) {
        this.slug = slug;
    }

    /** Replaces every field; blank ones are stored as null */
    public void update(String photoUrl, String bio, String soundcloudUrl, String bandcampUrl, String instagramUrl,
                       String websiteUrl, String bookingEmail, Instant updatedAt) {
        this.photoUrl = blankToNull(photoUrl);
        this.bio = blankToNull(bio);
        this.soundcloudUrl = blankToNull(soundcloudUrl);
        this.bandcampUrl = blankToNull(bandcampUrl);
        this.instagramUrl = blankToNull(instagramUrl);
        this.websiteUrl = blankToNull(websiteUrl);
        this.bookingEmail = blankToNull(bookingEmail);
        this.updatedAt = updatedAt;
    }

    public String getSlug() {
        return slug;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getBio() {
        return bio;
    }

    public String getSoundcloudUrl() {
        return soundcloudUrl;
    }

    public String getBandcampUrl() {
        return bandcampUrl;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getBookingEmail() {
        return bookingEmail;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role, Instant updatedAt) {
        this.role = role;
        this.updatedAt = updatedAt;
    }

    public void setDisplayName(String displayName, Instant updatedAt) {
        this.displayName = blankToNull(displayName);
        this.updatedAt = updatedAt;
    }
}
