package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An artist placed in a genre on the map: one of the satellites orbiting that genre's star. */
@Entity
@Table(name = "map_artist_genres")
public class MapArtistGenre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // As the map shows it, e.g. "Fungus Funk"
    @Column(name = "artist_name", nullable = false, length = 100)
    private String artistName;

    @Column(name = "genre_id", nullable = false, length = 30)
    private String genreId;

    protected MapArtistGenre() {
    }

    public MapArtistGenre(String artistName, String genreId) {
        this.artistName = artistName;
        this.genreId = genreId;
    }

    public Long getId() {
        return id;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getGenreId() {
        return genreId;
    }
}
