package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.OrganizerDto;
import com.psytrance.psytrance_tracker_backend.service.Organizers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Organizers of events (the ships' captains): public */
@RestController
@RequestMapping("/api/organizers")
public class OrganizerController {

    private final Organizers organizers;

    public OrganizerController(Organizers organizers) {
        this.organizers = organizers;
    }

    /** Example: {@code GET /api/organizers?search=cosmic}. Organizers with upcoming events, busiest first. */
    @GetMapping
    public List<OrganizerDto.Summary> getOrganizers(@RequestParam(required = false) String search) {
        return organizers.find(search);
    }

    /** Example: {@code GET /api/organizers/cosmic-circus}: upcoming and past events */
    @GetMapping("/{slug}")
    public OrganizerDto getOrganizer(@PathVariable String slug) {
        return organizers.get(slug);
    }
}
