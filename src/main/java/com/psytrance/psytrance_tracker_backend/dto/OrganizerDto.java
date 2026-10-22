package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/** An organizer's page: their upcoming events (soonest first) and the events they ran before (latest first) */
public record OrganizerDto(String slug, String name, String url, List<EventDto> upcomingEvents, List<PastGig> pastEvents) {

    /** One organizer in the list, with how many upcoming events they run */
    public record Summary(String slug, String name, long upcomingEvents) {
    }
}
