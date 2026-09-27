package com.psytrance.psytrance_tracker_backend.dto;

/**
 * One line of an event's line-up as the organizer wrote it, plus the artist name found in it
 * (null for headers, labels, URLs, "TBA" and anything else that doesn't look like an artist).
 */
public record LineUpLine(String text, String artist) {
}
