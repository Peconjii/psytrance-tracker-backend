package com.psytrance.psytrance_tracker_backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * An event as a ship on the galaxy map: where and when, plus its Map of Psy genres, biggest share first.
 * {@code lineUpRead} is false while the artist index hasn't read the line-up yet; then the empty
 * genre list means "not known yet" rather than "unknown genre".
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
        List<GenreShare> genres
) {

    public static MapEventDto from(EventDto event, boolean lineUpRead, List<GenreShare> genres) {
        return new MapEventDto(event.id(), event.nameParty(), event.nameType(), event.nameTown(), event.nameCountry(),
                event.dateStart(), event.dateEnd(), event.lat(), event.lon(), lineUpRead, genres);
    }
}
