package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.MapEventDto;
import com.psytrance.psytrance_tracker_backend.dto.MapPeriod;
import com.psytrance.psytrance_tracker_backend.service.MapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Data for the galaxy map (Map of Psy). */
@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    /** Example: {@code GET /api/map/events?period=MONTH}. Events with their Map of Psy genres, soonest first. */
    @GetMapping("/events")
    public List<MapEventDto> getEvents(@RequestParam(defaultValue = "WEEK") MapPeriod period) {
        return mapService.findEvents(period);
    }
}
