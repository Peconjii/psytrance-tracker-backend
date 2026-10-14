package com.psytrance.psytrance_tracker_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;

/**
 * One line of an event's line-up as the organizer wrote it, plus the artist name found in it
 * (null for headers, labels, URLs, "TBA" and anything else that doesn't look like an artist).
 */
public record LineUpLine(String text, String artist) {

    /** Where the artist's page is (/artists/{artistSlug}), or null when the line has no artist. */
    @JsonProperty("artistSlug")
    public String artistSlug() {
        return artist == null ? null : ArtistNames.slug(artist);
    }
}
