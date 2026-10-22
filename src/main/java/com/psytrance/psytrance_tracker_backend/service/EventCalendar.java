package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.exception.EventNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An event as a calendar file (.ics, RFC 5545), for "Add to calendar". Times are written in UTC from the start and end
 * with their UTC offset (as Goabase and our events give them), so every calendar shows them at the right hour.
 */
@Service
public class EventCalendar {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);
    // An event without an end gets this long, so it is not a dot in the calendar
    private static final Duration WITHOUT_END = Duration.ofHours(8);

    private final Parties parties;
    private final Clock clock;
    private final String frontendUrl;

    public EventCalendar(Parties parties, Clock clock, @Value("${app.frontend-url}") String frontendUrl) {
        this.parties = parties;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    public String ics(long eventId) {
        GoabasePartyDetails party = parties.fetchParty(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        OffsetDateTime start = parse(party.dateStart());
        if (start == null) {
            throw new EventNotFoundException(eventId);
        }
        OffsetDateTime end = parse(party.dateEnd());
        if (end == null || !end.isAfter(start)) {
            end = start.plus(WITHOUT_END);
        }
        String page = frontendUrl + "/events/" + eventId;
        String place = String.join(", ", nonBlank(party.nameTown(), party.nameCountry()));

        List<String> lines = new ArrayList<>(List.of(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Map of Psy//Events//EN",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:event-" + eventId + "@mapofpsy",
                "DTSTAMP:" + utc(clock.instant().atOffset(ZoneOffset.UTC)),
                "DTSTART:" + utc(start),
                "DTEND:" + utc(end),
                "SUMMARY:" + text(party.nameParty()),
                "LOCATION:" + text(place),
                "URL:" + page,
                "DESCRIPTION:" + text("Line-up, timetable and more: " + page)));
        if (party.geoLat() != null && party.geoLon() != null) {
            lines.add("GEO:" + party.geoLat() + ";" + party.geoLon());
        }
        lines.addAll(List.of("END:VEVENT", "END:VCALENDAR"));

        StringBuilder ics = new StringBuilder();
        lines.forEach(line -> ics.append(fold(line)).append("\r\n"));
        return ics.toString();
    }

    private static String utc(OffsetDateTime time) {
        return UTC.format(time.withOffsetSameInstant(ZoneOffset.UTC));
    }

    private static OffsetDateTime parse(String value) {
        try {
            return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Commas, semicolons, backslashes and line breaks are escaped in calendar text
    private static String text(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n");
    }

    // Lines longer than 75 bytes go on as lines starting with a space, without cutting a character in two
    static String fold(String line) {
        StringBuilder folded = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            String character = new String(Character.toChars(codePoint));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 75) {
                folded.append("\r\n ");
                bytes = 1;
            }
            folded.append(character);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        return folded.toString();
    }

    private static List<String> nonBlank(String... values) {
        List<String> kept = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                kept.add(value.strip());
            }
        }
        return kept;
    }
}
