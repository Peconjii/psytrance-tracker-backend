package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * The result of searching YouTube for a festival's aftermovie, stored so the search (which uses
 * up the daily YouTube quota) runs about once a month per festival instead of on every page view.
 * Stored in the database rather than in memory because the free hosting restarts the app often.
 * "Nothing found" is stored too (videoId null), so festivals without an aftermovie aren't searched again.
 */
@Entity
@Table(name = "aftermovie_lookups")
public class AftermovieLookup {

    // The festival's name without year or subtitle, e.g. "transition experience"; editions share it
    @Id
    @Column(name = "festival_key", length = 200)
    private String festivalKey;

    @Column(name = "video_id", length = 20)
    private String videoId;

    @Column(name = "title", length = 300)
    private String title;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected AftermovieLookup() {
    }

    public AftermovieLookup(String festivalKey, String videoId, String title, Instant checkedAt) {
        this.festivalKey = festivalKey;
        this.videoId = videoId;
        this.title = title;
        this.checkedAt = checkedAt;
    }

    public boolean found() {
        return videoId != null;
    }

    public String getFestivalKey() {
        return festivalKey;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
