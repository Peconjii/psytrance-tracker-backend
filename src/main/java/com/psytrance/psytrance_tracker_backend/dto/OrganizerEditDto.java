package com.psytrance.psytrance_tracker_backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the organizer's editor shows: their page, their upcoming events (to write timetables for) and, for
 * admins only, who owns the page
 */
public record OrganizerEditDto(String slug, String name, String photoUrl, String bio, String instagramUrl,
                               String websiteUrl, List<Event> events, String owner) {

    /** One of the organizer's upcoming events */
    public record Event(long id, String name, LocalDate dateStart, String town) {
    }
}
