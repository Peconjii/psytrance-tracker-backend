package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.CrewRequests;
import com.psytrance.psytrance_tracker_backend.dto.CrewView;
import com.psytrance.psytrance_tracker_backend.service.events.CrewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Crews of an event. Everything here needs login (SecurityConfig: anything not listed is authenticated). */
@RestController
public class CrewController {

    private final CrewService crewService;

    public CrewController(CrewService crewService) {
        this.crewService = crewService;
    }

    @PostMapping("/api/crews/event/{eventId}")
    public CrewView create(@PathVariable long eventId, @Valid @RequestBody CrewRequests.NewCrew body, Authentication auth) {
        return crewService.create(auth.getName(), eventId, body.name());
    }

    @GetMapping("/api/crews/mine")
    public List<CrewView.Summary> mine(Authentication auth) {
        return crewService.mine(auth.getName());
    }

    @GetMapping("/api/crews/event/{eventId}")
    public List<CrewView.Summary> mineForEvent(@PathVariable long eventId, Authentication auth) {
        return crewService.mineForEvent(auth.getName(), eventId);
    }

    /** What the link shows before joining */
    @GetMapping("/api/crews/{code}/preview")
    public CrewView.Summary preview(@PathVariable String code, Authentication auth) {
        return crewService.preview(code, auth.getName());
    }

    @GetMapping("/api/crews/{code}")
    public CrewView view(@PathVariable String code, Authentication auth) {
        return crewService.view(code, auth.getName());
    }

    @PostMapping("/api/crews/{code}/join")
    public CrewView join(@PathVariable String code, Authentication auth) {
        return crewService.join(code, auth.getName());
    }

    @DeleteMapping("/api/crews/{code}/members/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String code, @PathVariable String username, Authentication auth) {
        crewService.remove(code, auth.getName(), username);
    }

    @PostMapping("/api/crews/{code}/link")
    public CrewView newLink(@PathVariable String code, Authentication auth) {
        return crewService.newLink(code, auth.getName());
    }

    @PutMapping("/api/crews/{code}/camp")
    public CrewView camp(@PathVariable String code, @Valid @RequestBody CrewRequests.CampSpot body, Authentication auth) {
        return crewService.setCampSpot(code, auth.getName(), body.campSpot());
    }

    @PostMapping("/api/crews/{code}/items")
    public CrewView addItem(@PathVariable String code, @Valid @RequestBody CrewRequests.NewItem body, Authentication auth) {
        return crewService.addItem(code, auth.getName(), body.name());
    }

    @PostMapping("/api/crews/{code}/items/{itemId}/bring")
    public CrewView bring(@PathVariable String code, @PathVariable long itemId, Authentication auth) {
        return crewService.toggleBring(code, auth.getName(), itemId);
    }

    @DeleteMapping("/api/crews/{code}/items/{itemId}")
    public CrewView deleteItem(@PathVariable String code, @PathVariable long itemId, Authentication auth) {
        return crewService.deleteItem(code, auth.getName(), itemId);
    }

    @PostMapping("/api/crews/{code}/picks")
    public CrewView pick(@PathVariable String code, @Valid @RequestBody CrewRequests.Pick body, Authentication auth) {
        return crewService.pick(code, auth.getName(), body.artist(), body.startsAt());
    }

    @DeleteMapping("/api/crews/{code}/picks")
    public CrewView unpick(@PathVariable String code, @Valid @RequestBody CrewRequests.Pick body, Authentication auth) {
        return crewService.unpick(code, auth.getName(), body.artist(), body.startsAt());
    }
}
