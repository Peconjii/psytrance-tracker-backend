package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.*;

/** A festival the artist says they played, with the year and a link (a set, a line-up) when they have one */
@Entity
@Table(name = "artist_festivals")
public class ArtistFestival {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artist_slug", nullable = false, length = 100)
    private String artistSlug;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "year")
    private Integer year;

    @Column(name = "url", length = 300)
    private String url;

    protected ArtistFestival() {
    }

    public ArtistFestival(String artistSlug, int position, String name, Integer year, String url) {
        this.artistSlug = artistSlug;
        this.position = position;
        this.name = name;
        this.year = year;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public Integer getYear() {
        return year;
    }

    public String getUrl() {
        return url;
    }
}
