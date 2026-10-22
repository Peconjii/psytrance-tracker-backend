package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.Organizer;

import java.util.List;

/**
 * An organizer's page: Goabase's address for them, the page they wrote (null when nobody did), their upcoming events
 * (soonest first) and the events they ran before (latest first)
 */
public record OrganizerDto(String slug, String name, String url, Profile profile, List<EventDto> upcomingEvents,
                           List<PastGig> pastEvents) {

    /** What the organizer wrote on their page; every field can be null */
    public record Profile(String photoUrl, String bio, String instagramUrl, String websiteUrl) {

        public static Profile of(Organizer organizer) {
            return new Profile(organizer.getPhotoUrl(), organizer.getBio(), organizer.getInstagramUrl(), organizer.getWebsiteUrl());
        }
    }

    /** One organizer in the list, with how many upcoming events they run */
    public record Summary(String slug, String name, long upcomingEvents) {
    }
}
