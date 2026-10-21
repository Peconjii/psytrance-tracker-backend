package com.psytrance.psytrance_tracker_backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An event's timetable, in the order the sets were written: {@code manual} when an admin set it, otherwise
 * read from the line-up. Empty when the line-up has no set times. Times are on the clock at the venue;
 * {@code utcOffsetMinutes} relates that clock to UTC (null when unknown).
 */
public record TimetableDto(Long eventId, boolean manual, Integer utcOffsetMinutes, List<Set> sets) {

    /** One set; {@code artistSlug} is the artist page, {@code endsAt} is null when only the start is known */
    public record Set(String stage, String artist, String artistSlug, LocalDateTime startsAt, LocalDateTime endsAt) {
    }
}
