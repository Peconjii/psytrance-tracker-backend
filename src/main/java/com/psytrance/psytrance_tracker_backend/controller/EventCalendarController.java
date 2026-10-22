package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.EventCalendar;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** "Add to calendar": an event as a .ics file, which phones and calendar apps open. Public, like the event pages. */
@RestController
public class EventCalendarController {

    private static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar; charset=UTF-8");

    private final EventCalendar eventCalendar;

    public EventCalendarController(EventCalendar eventCalendar) {
        this.eventCalendar = eventCalendar;
    }

    /** Example: {@code GET /api/events/118452/calendar.ics} */
    @GetMapping("/api/events/{id}/calendar.ics")
    public ResponseEntity<String> calendar(@PathVariable long id) {
        return ResponseEntity.ok()
                .contentType(CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("event-" + id + ".ics").build().toString())
                .body(eventCalendar.ics(id));
    }
}
