package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.events.TicketClicks;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Clicks on an event's ticket button: anyone counts one, only admins read them */
@RestController
public class TicketClickController {

    private final TicketClicks ticketClicks;
    private final Admins admins;

    public TicketClickController(TicketClicks ticketClicks, Admins admins) {
        this.ticketClicks = ticketClicks;
        this.admins = admins;
    }

    public record Click(String name) {
    }

    /** Example: {@code POST /api/events/123/ticket-click {"name": "Ozora Festival 2027"}}; 400 for an id that is no event */
    @PostMapping("/api/events/{id}/ticket-click")
    public ResponseEntity<Void> click(@PathVariable String id, @RequestBody(required = false) Click click) {
        try {
            ticketClicks.count(id, click == null ? null : click.name());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }

    /** Example: {@code GET /api/admin/ticket-clicks?days=30}: the most clicked events of the last 30 days */
    @GetMapping("/api/admin/ticket-clicks")
    public List<TicketClicks.EventClicks> top(@RequestParam(defaultValue = "30") int days, Authentication authentication) {
        admins.requireAdmin(authentication);
        return ticketClicks.top(Math.max(1, Math.min(days, 365)), 20);
    }
}
