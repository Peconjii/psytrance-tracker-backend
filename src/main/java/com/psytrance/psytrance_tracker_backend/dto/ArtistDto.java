package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/**
 * An artist's page: their name (the approved display name if they have one), the upcoming events they play (soonest
 * first), their profile (null when nobody wrote one yet), the events they played (latest first), how many users
 * follow them, the tracks and festivals the artist put on their profile, the role they picked (null: not picked)
 * and the share of their gigs the line-ups wrote "live" for (to guess a role).
 */
public record ArtistDto(String slug, String name, List<EventDto> upcomingEvents, ArtistProfileDto profile,
                        List<PastGig> pastEvents, long followers,
                        List<ArtistListsRequest.Track> tracks, List<ArtistListsRequest.Festival> festivals,
                        String role, double liveShare) {
}
