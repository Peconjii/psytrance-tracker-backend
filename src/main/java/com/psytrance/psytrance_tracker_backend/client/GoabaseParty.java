package com.psytrance.psytrance_tracker_backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One party exactly as Goabase sends it. Only the fields we use are listed;
 * everything else in their JSON is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoabaseParty(
        Long id,
        String nameParty,
        String dateStart,
        String dateEnd,
        String nameType,
        String nameStatus,
        String nameCountry,
        String nameTown,
        Double geoLat,
        Double geoLon,
        String urlImageMedium,
        String urlPartyHtml
) {
}
