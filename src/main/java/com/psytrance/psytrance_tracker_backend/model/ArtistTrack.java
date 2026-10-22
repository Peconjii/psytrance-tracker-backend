package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

/** A track the artist picked for their profile: a title and a link to it (YouTube, SoundCloud, Bandcamp...) */
@Entity
@Table(name = "artist_tracks")
public class ArtistTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artist_slug", nullable = false, length = 100)
    private String artistSlug;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "url", nullable = false, length = 300)
    private String url;

    protected ArtistTrack() {
    }

    public ArtistTrack(String artistSlug, int position, String title, String url) {
        this.artistSlug = artistSlug;
        this.position = position;
        this.title = title;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }
}
