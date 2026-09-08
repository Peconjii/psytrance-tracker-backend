package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.GoabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goabase")
public class GoabaseController {

    @Autowired
    private GoabaseService goabaseService;

    @GetMapping("/events")
    public ResponseEntity<?> getEvents(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(goabaseService.getEvents(limit));
    }

    @GetMapping("/events/country")
    public ResponseEntity<?> getEventsByCountry(
            @RequestParam String country,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(goabaseService.getEventsByCountry(country, limit));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<?> getEventById(@PathVariable int id) {
        return ResponseEntity.ok(goabaseService.getEventById(id));
    }
}