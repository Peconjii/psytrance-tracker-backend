package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.EventSearch;
import com.psytrance.psytrance_tracker_backend.dto.EventTimeline;
import com.psytrance.psytrance_tracker_backend.dto.PageResponse;
import com.psytrance.psytrance_tracker_backend.service.EventService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Example: {@code GET /api/events?page=0&size=24&search=boom&country=portugal&timeline=UPCOMING}
     */
    @GetMapping
    public PageResponse<EventDto> getEvents(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "ALL") EventTimeline timeline) {
        return eventService.findEvents(new EventSearch(search, country, genre, timeline), page, size);
    }

    /** Every event that has coordinates, for the world map. */
    @GetMapping("/map")
    public List<EventDto> getMapEvents() {
        return eventService.findEventsWithCoordinates();
    }

    @GetMapping("/{id}")
    public EventDto getEvent(@PathVariable long id) {
        return eventService.findById(id);
    }
}
