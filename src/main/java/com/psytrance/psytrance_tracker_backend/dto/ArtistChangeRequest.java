package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.ArtistRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /api/artists/{slug}/requests}: other map genres ({@code GENRES} with {@code genres}) or another name on
 * the page ({@code NAME} with {@code name}). An admin decides; an admin's own request applies at once.
 */
public record ArtistChangeRequest(
        @NotNull ArtistRequest.Kind kind,
        @Size(max = 4) List<String> genres,
        @Size(max = 100) String name) {
}
