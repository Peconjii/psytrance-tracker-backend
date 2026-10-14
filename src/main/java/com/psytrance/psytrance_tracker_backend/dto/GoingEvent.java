package com.psytrance.psytrance_tracker_backend.dto;

import com.psytrance.psytrance_tracker_backend.model.Attendance;

import java.time.LocalDate;

/** An event the user is going to, as their profile lists it. */
public record GoingEvent(Long eventId, String eventName, LocalDate startsOn) {

    public static GoingEvent from(Attendance attendance) {
        return new GoingEvent(attendance.getEventId(), attendance.getEventName(), attendance.getStartsOn());
    }
}
