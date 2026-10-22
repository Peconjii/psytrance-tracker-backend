package com.psytrance.psytrance_tracker_backend.dto;

import java.util.Set;

/**
 * Filters from the events page. Blank values mean "don't filter by this". {@code genre} is a word looked for in the
 * event's type and name; {@code eventIds}, when not null, keeps only these events (the ones of a map genre);
 * {@code near}, when not null, keeps the events within that many kilometres ("Near me").
 */
public record EventSearch(String search, String country, String genre, EventTimeline timeline, Set<Long> eventIds,
                          Near near) {

    /** A point (the visitor's, from the browser) and how far from it: {@code km} */
    public record Near(double lat, double lon, int km) {
    }

    public EventSearch {
        timeline = timeline == null ? EventTimeline.ALL : timeline;
    }

    public EventSearch(String search, String country, String genre, EventTimeline timeline, Set<Long> eventIds) {
        this(search, country, genre, timeline, eventIds, null);
    }

    public EventSearch(String search, String country, String genre, EventTimeline timeline) {
        this(search, country, genre, timeline, null, null);
    }
}
