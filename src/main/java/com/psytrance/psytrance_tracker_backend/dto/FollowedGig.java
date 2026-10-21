package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/** An upcoming event where artists the user follows play, for the gold ships on the map */
public record FollowedGig(long eventId, List<String> artists) {
}
