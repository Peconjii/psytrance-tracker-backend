package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.ArtistRequest;

import java.time.Instant;

/** A request of an artist's owner, for the edit page and the admin's list; value is genre ids or a name */
public record ArtistRequestDto(long id, String artistSlug, String artistName, ArtistRequest.Kind kind, String value,
                               String username, ArtistRequest.Status status, Instant createdAt) {
}
