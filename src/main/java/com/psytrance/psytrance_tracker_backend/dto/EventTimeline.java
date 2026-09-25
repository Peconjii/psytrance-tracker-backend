package com.psytrance.psytrance_tracker_backend.dto;

/**
 * The "Event Timeline" filter on the events page.
 */
public enum EventTimeline {
    ALL,
    /** Not finished yet - includes events that are happening right now. */
    UPCOMING,
    /** Running at any point between this Friday and Sunday. */
    THIS_WEEKEND,
    /** Already finished. */
    PAST
}
