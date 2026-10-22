package com.psytrance.psytrance_tracker_backend.dto;

import java.util.Set;

/**
 * Filters from the events page. Blank values mean "don't filter by this". {@code genre} is a word looked for in the
 * event's type and name; {@code eventIds}, when not null, keeps only these events (the ones of a map genre).
 */
public record EventSearch(String search, String country, String genre, EventTimeline timeline, Set<Long> eventIds) {

    public EventSearch {
        timeline = timeline == null ? EventTimeline.ALL : timeline;
    }

    public EventSearch(String search, String country, String genre, EventTimeline timeline) {
        this(search, country, genre, timeline, null);
    }
}
