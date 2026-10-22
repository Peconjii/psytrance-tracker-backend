package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.OurEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * An event added on our site, for the account that sent it and for admins. {@code eventId} is its id in lists and on
 * the map once approved. Admins also see who sent it and Goabase events that may be the same one.
 */
public record OurEventDto(
        long id,
        Long eventId,
        String status,
        String name,
        String eventType,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String timeZone,
        String country,
        String town,
        double lat,
        double lon,
        String lineUp,
        String locationText,
        String entryFee,
        String info,
        String organizerName,
        String organizerUrl,
        String ticketUrl,
        String imageUrl,
        List<String> genres,
        Instant createdAt,
        String rejectReason,
        String submittedBy,
        List<Duplicate> possibleDuplicates
) {
    /** A Goabase event on about the same days, in the same town, or in the same country with a word of the name in common */
    public record Duplicate(long id, String name, LocalDate dateStart, String town) {
    }

    public static OurEventDto of(OurEvent event, String submittedBy, List<Duplicate> possibleDuplicates) {
        return new OurEventDto(event.getId(), OurEvent.APPROVED.equals(event.getStatus()) ? event.eventId() : null,
                event.getStatus(), event.getName(), event.getEventType(), event.getStartsAt(), event.getEndsAt(),
                event.getTimeZone(), event.getCountry(), event.getTown(), event.getLat(), event.getLon(), event.getLineUp(),
                event.getLocationText(), event.getEntryFee(), event.getInfo(), event.getOrganizerName(),
                event.getOrganizerUrl(), event.getTicketUrl(), event.getImageUrl(), event.genreIds(), event.getCreatedAt(),
                event.getRejectReason(), submittedBy, possibleDuplicates);
    }
}
