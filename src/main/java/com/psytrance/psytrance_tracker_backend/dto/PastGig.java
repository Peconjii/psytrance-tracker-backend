package com.psytrance.psytrance_tracker_backend.dto;

import java.time.LocalDate;

/** An event an artist played, from the line-ups read before it was over */
public record PastGig(long eventId, String name, LocalDate dateStart, String town, String country) {
}
