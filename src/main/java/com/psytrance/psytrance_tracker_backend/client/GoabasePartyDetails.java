package com.psytrance.psytrance_tracker_backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One party as Goabase sends it from its single-party endpoint, which has more
 * than the list: line-up, venue notes, entry fee, description and organizer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoabasePartyDetails(
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
        String urlImageLarge,
        String urlParty,
        String textLineUp,
        String textLocation,
        String textEntryFee,
        String textMore,
        String nameOrganizer,
        String urlOrganizer
) {

    /** The same party in the shape the list endpoint uses (which calls the page link urlPartyHtml). */
    public GoabaseParty summary() {
        return new GoabaseParty(id, nameParty, dateStart, dateEnd, nameType, nameStatus,
                nameCountry, nameTown, geoLat, geoLon, urlImageMedium, urlParty);
    }
}
