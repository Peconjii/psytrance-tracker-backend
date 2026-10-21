package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/**
 * An artist in the admin panel: the name, the slug of their artist page, the map genres they're placed in
 * (empty when not on the map) and whether they orbit any star.
 */
public record AdminArtistDto(String name, String slug, List<String> genres, boolean onMap) {
}
