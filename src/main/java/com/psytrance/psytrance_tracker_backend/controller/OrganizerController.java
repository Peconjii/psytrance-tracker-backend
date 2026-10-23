package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.OrganizerDto;
import com.psytrance.psytrance_tracker_backend.dto.OrganizerEditDto;
import com.psytrance.psytrance_tracker_backend.dto.OrganizerProfileRequest;
import com.psytrance.psytrance_tracker_backend.dto.TimetableDto;
import com.psytrance.psytrance_tracker_backend.service.OrganizerEditing;
import com.psytrance.psytrance_tracker_backend.service.Organizers;
import com.psytrance.psytrance_tracker_backend.service.FreshPages;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Organizers of events (the ships' captains): public */
@RestController
@RequestMapping("/api/organizers")
public class OrganizerController {

    private final Organizers organizers;
    private final OrganizerEditing organizerEditing;
    private final FreshPages freshPages;

    public OrganizerController(Organizers organizers, OrganizerEditing organizerEditing, FreshPages freshPages) {
        this.organizers = organizers;
        this.organizerEditing = organizerEditing;
        this.freshPages = freshPages;
    }

    /** Example: {@code GET /api/organizers?search=cosmic}. Organizers with upcoming events, busiest first. */
    @GetMapping
    public List<OrganizerDto.Summary> getOrganizers(@RequestParam(required = false) String search) {
        return organizers.find(search);
    }

    /** Organizers with their own page and no upcoming event yet, newest first */
    @GetMapping("/new-pages")
    public List<FreshPages.Page> getNewPages(@RequestParam(required = false) String search) {
        return freshPages.organizers(search);
    }

    /** Example: {@code GET /api/organizers/cosmic-circus}: upcoming and past events */
    @GetMapping("/{slug}")
    public OrganizerDto getOrganizer(@PathVariable String slug) {
        return organizers.get(slug);
    }

    // Editing an organizer: their owner (an account an admin linked to them) and admins. GET is public in
    // SecurityConfig, so the editor checks the login itself (401 for guests, 403 for other users).

    @GetMapping("/{slug}/edit")
    public OrganizerEditDto getEdit(@PathVariable String slug, Authentication authentication) {
        return organizerEditing.edit(slug, username(authentication));
    }

    @PutMapping("/{slug}/profile")
    public OrganizerDto.Profile setProfile(@PathVariable String slug, @Valid @RequestBody OrganizerProfileRequest profile,
                                           Authentication authentication) {
        return organizerEditing.saveProfile(slug, profile, username(authentication));
    }

    /** The timetable of one of the organizer's own events: it gives the event its live fleet on the map */
    @PutMapping("/{slug}/events/{eventId}/timetable")
    public TimetableDto setTimetable(@PathVariable String slug, @PathVariable long eventId,
                                     @Valid @RequestBody AdminEventController.TimetableChange change,
                                     Authentication authentication) {
        return organizerEditing.setTimetable(slug, eventId, change.sets(), username(authentication));
    }

    /** Back to the set times written in the line-up */
    @DeleteMapping("/{slug}/events/{eventId}/timetable")
    public TimetableDto handBackTimetable(@PathVariable String slug, @PathVariable long eventId, Authentication authentication) {
        return organizerEditing.handBackTimetable(slug, eventId, username(authentication));
    }

    private static String username(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken ? null : authentication.getName();
    }
}
