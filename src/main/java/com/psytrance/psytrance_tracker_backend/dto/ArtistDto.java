package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/** An artist's page: their name and the upcoming events they play, soonest first. */
public record ArtistDto(String slug, String name, List<EventDto> upcomingEvents) {
}
