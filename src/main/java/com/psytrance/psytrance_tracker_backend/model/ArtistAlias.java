package com.psytrance.psytrance_tracker_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Another spelling of an artist ("AJJA" for "Ajja Experience"): line-ups that write it count for the artist. */
@Entity
@Table(name = "artist_aliases")
public class ArtistAlias {

    @Id
    @Column(name = "alias_slug", length = 100)
    private String aliasSlug;

    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    protected ArtistAlias() {
    }

    public ArtistAlias(String aliasSlug, Long artistId) {
        this.aliasSlug = aliasSlug;
        this.artistId = artistId;
    }

    public String getAliasSlug() {
        return aliasSlug;
    }

    public Long getArtistId() {
        return artistId;
    }

    public void setArtistId(Long artistId) {
        this.artistId = artistId;
    }
}
