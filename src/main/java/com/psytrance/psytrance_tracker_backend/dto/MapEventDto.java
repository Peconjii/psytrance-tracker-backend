package com.psytrance.psytrance_tracker_backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * An event as a ship on the galaxy map: where and when, plus its Map of Psy genres, biggest share first.
 * {@code lineUpRead} is false while the artist index hasn't read the line-up yet; then the empty
 * genre list means "not known yet" rather than "unknown genre". {@code featured}: an admin features it (with an
 * optional note), so its ship is marked and it leads the departures. {@code festival}: a festival on the map (kind
 * Festival, three days or more): a bigger ship, on the map the whole time until it is over. {@code festivalId}: the
 * festival station it is an edition of, null when none.
 */
public record MapEventDto(
        Long id,
        String nameParty,
        String nameType,
        String nameTown,
        String nameCountry,
        LocalDate dateStart,
        LocalDate dateEnd,
        Double lat,
        Double lon,
        boolean lineUpRead,
        List<GenreShare> genres,
        boolean featured,
        String featuredNote,
        boolean festival,
        String festivalId
) {

    public static MapEventDto from(EventDto event, boolean lineUpRead, List<GenreShare> genres, String featuredNote,
                                   boolean festival, String festivalId) {
        return new MapEventDto(event.id(), event.nameParty(), event.nameType(), event.nameTown(), event.nameCountry(),
                event.dateStart(), event.dateEnd(), event.lat(), event.lon(), lineUpRead, genres, featuredNote != null,
                featuredNote == null || featuredNote.isEmpty() ? null : featuredNote, festival, festivalId);
    }
}
