package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.AftermovieDto;
import com.psytrance.psytrance_tracker_backend.dto.EventDetailsDto;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.EventSearch;
import com.psytrance.psytrance_tracker_backend.dto.EventTimeline;
import com.psytrance.psytrance_tracker_backend.dto.PageResponse;
import com.psytrance.psytrance_tracker_backend.dto.TimetableDto;
import com.psytrance.psytrance_tracker_backend.repository.EventGenreRepository;
import com.psytrance.psytrance_tracker_backend.service.AftermovieService;
import com.psytrance.psytrance_tracker_backend.service.EventService;
import com.psytrance.psytrance_tracker_backend.service.EventTimetables;
import com.psytrance.psytrance_tracker_backend.service.MapGenres;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
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
    private final AftermovieService aftermovieService;
    private final EventTimetables eventTimetables;
    private final MapGenres mapGenres;
    private final EventGenreRepository eventGenreRepository;

    public EventController(EventService eventService, AftermovieService aftermovieService, EventTimetables eventTimetables,
                           MapGenres mapGenres, EventGenreRepository eventGenreRepository) {
        this.eventService = eventService;
        this.aftermovieService = aftermovieService;
        this.eventTimetables = eventTimetables;
        this.mapGenres = mapGenres;
        this.eventGenreRepository = eventGenreRepository;
    }

    /** The set times of the event: read from its line-up or set by an admin; no sets when there are none */
    @GetMapping("/{id}/timetable")
    public TimetableDto getTimetable(@PathVariable long id) {
        return eventTimetables.get(id);
    }

    /**
     * Example: {@code GET /api/events?page=0&size=24&search=boom&country=portugal&genre=forest&timeline=THIS_WEEKEND}.
     * A genre of the map ("forest") keeps the events that have it on the map; another word is looked for in the name.
     */
    @GetMapping
    public PageResponse<EventDto> getEvents(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "ALL") EventTimeline timeline) {
        if (genre != null && mapGenres.isGenre(genre)) {
            return eventService.findEvents(new EventSearch(search, country, null, timeline,
                    eventGenreRepository.findEventIdsByGenreId(genre)), page, size);
        }
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

    /** The full event page: times, line-up, venue notes, entry fee, description and organizer. */
    @GetMapping("/{id}/details")
    public EventDetailsDto getEventDetails(@PathVariable long id) {
        return eventService.findDetails(id);
    }

    /** The festival's own aftermovie on YouTube, or 204 No Content when there is none. */
    @GetMapping("/{id}/aftermovie")
    public ResponseEntity<AftermovieDto> getAftermovie(@PathVariable long id) {
        return aftermovieService.findForEvent(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
