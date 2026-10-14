package com.psytrance.psytrance_tracker_backend.dto;

/** How many people go to an event, and whether the logged-in user is one of them (false for guests). */
public record GoingStatus(long count, boolean going) {
}
