package com.psytrance.psytrance_tracker_backend.dto;

/**
 * Filters from the events page. Blank values mean "don't filter by this".
 */
public record EventSearch(String search, String country, String genre, EventTimeline timeline) {

    public EventSearch {
        timeline = timeline == null ? EventTimeline.ALL : timeline;
    }
}
