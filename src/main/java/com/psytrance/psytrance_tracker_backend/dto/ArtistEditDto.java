package com.psytrance.psytrance_tracker_backend.dto;

import java.util.List;

/**
 * Everything the profile editor shows: the profile, tracks and festivals the owner wrote, the map genres the artist
 * has now, the owner's requests still waiting for an admin, and (for admins only) who owns the profile.
 */
public record ArtistEditDto(String slug, String name, ArtistProfileDto profile,
                            List<ArtistListsRequest.Track> tracks, List<ArtistListsRequest.Festival> festivals,
                            List<String> genres, List<ArtistRequestDto> pendingRequests, String owner) {
}
