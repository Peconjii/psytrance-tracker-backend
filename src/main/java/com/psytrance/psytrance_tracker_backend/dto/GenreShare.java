package com.psytrance.psytrance_tracker_backend.dto;

/**
 * One Map of Psy genre of an event: its id ("forest"), its part of the event (all shares add up to 1)
 * and the best evidence for it.
 */
public record GenreShare(String genreId, double share, Source source) {

    /** Where a genre comes from, most reliable first. */
    public enum Source {
        /** An artist in the line-up is placed in this genre on the map. */
        ARTIST,
        /** A stage header in the line-up names the genre ("DARKPSY / FOREST STAGE"). */
        STAGE,
        /** The organizer picked the genre on Goabase. */
        KEYWORD
    }
}
