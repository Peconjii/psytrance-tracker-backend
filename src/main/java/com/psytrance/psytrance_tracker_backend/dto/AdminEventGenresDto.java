package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/**
 * An event in the admin panel: its genres, biggest share first, and whether an admin set them by hand
 * ({@code manual}) or the artist index worked them out from the line-up.
 */
public record AdminEventGenresDto(Long id, String nameParty, List<GenreShare> genres, boolean manual) {
}
