package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/**
 * An artist's page: their name, the upcoming events they play (soonest first), their profile (null when nobody
 * wrote one yet), the events they played (latest first) and how many users follow them.
 */
public record ArtistDto(String slug, String name, List<EventDto> upcomingEvents, ArtistProfileDto profile,
                        List<PastGig> pastEvents, long followers) {
}
