package com.psytrance.psytrance_tracker_backend.dto;

/** An artist in the artist list, with how many upcoming events they play. */
public record ArtistSummary(String slug, String name, Long upcomingEvents) {
}
