package com.psytrance.psytrance_tracker_backend.dto;

/** How far ahead the galaxy map shows events: the week or month buttons on the map. */
public enum MapPeriod {
    WEEK(7),
    MONTH(30);

    private final int days;

    MapPeriod(int days) {
        this.days = days;
    }

    /** Number of days, today included. */
    public int days() {
        return days;
    }
}
