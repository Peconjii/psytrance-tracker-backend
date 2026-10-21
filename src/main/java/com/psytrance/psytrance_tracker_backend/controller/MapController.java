package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.GenreShare;
import com.psytrance.psytrance_tracker_backend.dto.MapEventDto;
import com.psytrance.psytrance_tracker_backend.dto.MapPeriod;
import com.psytrance.psytrance_tracker_backend.dto.TimetableDto;
import com.psytrance.psytrance_tracker_backend.service.EventTimetables;
import com.psytrance.psytrance_tracker_backend.service.MapPlacements;
import com.psytrance.psytrance_tracker_backend.service.MapService;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Data for the galaxy map (Map of Psy). */
@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService mapService;
    private final MapPlacements mapPlacements;
    private final EventTimetables eventTimetables;

    public MapController(MapService mapService, MapPlacements mapPlacements, EventTimetables eventTimetables) {
        this.mapService = mapService;
        this.eventTimetables = eventTimetables;
        this.mapPlacements = mapPlacements;
    }

    /** Which artists orbit which genre star: {@code {"forest": ["Derango", ...], ...}}. Admins edit it in the panel. */
    @GetMapping("/placements")
    public Map<String, List<String>> getPlacements() {
        return mapPlacements.placements();
    }

    /** Example: {@code GET /api/map/events?period=MONTH}. Events with their Map of Psy genres, soonest first. */
    @GetMapping("/events")
    public List<MapEventDto> getEvents(@RequestParam(defaultValue = "WEEK") MapPeriod period) {
        return mapService.findEvents(period);
    }

    /**
     * {@code GET /api/map/timetables?period=WEEK}: the timetables of the period's events that have set times, so the
     * map can send a ship to each genre's star when it plays. Times are on the venue clock, with its UTC offset.
     */
    @GetMapping("/timetables")
    public List<TimetableDto> getTimetables(@RequestParam(defaultValue = "WEEK") MapPeriod period) {
        return eventTimetables.forEvents(mapService.findEvents(period).stream().map(MapEventDto::id).toList());
    }

    /**
     * The Map of Psy genres of a whole page of events in one request, e.g. {@code GET /api/map/genres?ids=1,2,3}
     * answers {@code {"1": [{"genreId": "forest", "share": 0.75, "source": "ARTIST"}, ...]}}. Events with
     * no known genre are left out.
     */
    @GetMapping("/genres")
    public Map<Long, List<GenreShare>> getGenres(@RequestParam @Size(max = 100) List<Long> ids) {
        return mapService.findGenres(ids);
    }
}
