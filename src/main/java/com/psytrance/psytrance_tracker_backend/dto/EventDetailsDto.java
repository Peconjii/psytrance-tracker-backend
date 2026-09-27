package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.util.LineUpParser;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Everything the event page shows. It has the same basic fields as {@link EventDto},
 * so the frontend can treat it as an event (favorites, weather, map), plus start and
 * end times and Goabase's free-text sections. Empty texts are sent as null; the line-up is
 * split into lines with the artist name found in each (empty list when there is none).
 */
public record EventDetailsDto(
        Long id,
        String nameParty,
        String nameTown,
        String nameCountry,
        String nameType,
        String nameStatus,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String urlImageMedium,
        String urlImageLarge,
        String urlPartyHtml,
        Double lat,
        Double lon,
        List<LineUpLine> lineUp,
        String location,
        String entryFee,
        String info,
        String organizer,
        String organizerUrl
) {

    public static EventDetailsDto from(GoabasePartyDetails party) {
        EventDto basics = EventDto.from(party.summary());
        return new EventDetailsDto(
                basics.id(),
                basics.nameParty(),
                basics.nameTown(),
                basics.nameCountry(),
                basics.nameType(),
                basics.nameStatus(),
                parseDateTime(party.dateStart()),
                parseDateTime(party.dateEnd()),
                basics.urlImageMedium(),
                party.urlImageLarge(),
                basics.urlPartyHtml(),
                basics.lat(),
                basics.lon(),
                LineUpParser.parse(textOrNull(party.textLineUp())),
                textOrNull(party.textLocation()),
                textOrNull(party.textEntryFee()),
                textOrNull(party.textMore()),
                textOrNull(party.nameOrganizer()),
                toLink(party.urlOrganizer())
        );
    }

    // Goabase sends "2026-09-22T22:00:00+02:00"; we keep the time as it is on the clock at the venue
    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Goabase texts use Windows line breaks and are often just ""
    private static String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.replace("\r\n", "\n").strip();
    }

    // Organizers often enter "www.example.org", which a browser would treat as a relative link
    private static String toLink(String url) {
        String value = textOrNull(url);
        if (value == null) {
            return null;
        }
        return value.startsWith("http://") || value.startsWith("https://") ? value : "https://" + value;
    }
}
