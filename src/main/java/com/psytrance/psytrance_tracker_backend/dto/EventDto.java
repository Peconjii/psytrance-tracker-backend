package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * A cleaned-up event as our API returns it. Field names match what the React
 * cards already read, so the frontend didn't need to change its components.
 */
public record EventDto(
        Long id,
        String nameParty,
        String nameTown,
        String nameCountry,
        String nameType,
        String nameStatus,
        LocalDate dateStart,
        LocalDate dateEnd,
        String urlImageMedium,
        String urlPartyHtml,
        Double lat,
        Double lon
) {

    public static EventDto from(GoabaseParty party) {
        return new EventDto(
                party.id(),
                orDefault(party.nameParty(), "Untitled Event"),
                orDefault(party.nameTown(), "Unknown Location"),
                orDefault(party.nameCountry(), ""),
                orDefault(party.nameType(), "Party"),
                party.nameStatus(),
                parseDate(party.dateStart()),
                parseDate(party.dateEnd()),
                party.urlImageMedium(),
                party.urlPartyHtml(),
                party.geoLat(),
                party.geoLon()
        );
    }

    public boolean hasCoordinates() {
        return lat != null && lon != null;
    }

    /** Last day of the event; single-day events only have a start date. */
    public LocalDate lastDay() {
        return dateEnd != null ? dateEnd : dateStart;
    }

    // Goabase sends "2026-09-22T22:00:00+02:00"; we keep the local calendar day of the event
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
