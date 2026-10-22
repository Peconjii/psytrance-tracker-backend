package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.OurEventDto;
import com.psytrance.psytrance_tracker_backend.dto.OurEventRequest;
import com.psytrance.psytrance_tracker_backend.service.OurEvents;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adding events on our site. Everything here needs login (see SecurityConfig); adding also a confirmed email. */
@RestController
@RequestMapping("/api/our-events")
public class OurEventController {

    private final OurEvents ourEvents;

    public OurEventController(OurEvents ourEvents) {
        this.ourEvents = ourEvents;
    }

    /** A new event: waits for an admin (status PENDING), or is on the map right away for admins and its organizer */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OurEventDto submit(@Valid @RequestBody OurEventRequest request, Authentication authentication) {
        return ourEvents.submit(request, authentication.getName());
    }

    /** The kinds of event the form offers */
    @GetMapping("/types")
    public List<String> types() {
        return OurEvents.TYPES;
    }

    @GetMapping("/mine")
    public List<OurEventDto> mine(Authentication authentication) {
        return ourEvents.mine(authentication.getName());
    }

    /** Takes back an event that is still waiting */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable long id, Authentication authentication) {
        ourEvents.withdraw(id, authentication.getName());
    }
}
