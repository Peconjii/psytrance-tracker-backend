package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.ArtistIndexer;
import com.psytrance.psytrance_tracker_backend.service.EventFeaturing;
import com.psytrance.psytrance_tracker_backend.service.EventHiding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Admin tools for one event: read its line-up again now, hide it from the site or show it again, feature it on the map */
@RestController
@RequestMapping("/api/admin/events")
public class AdminEventToolsController {

    private final ArtistIndexer artistIndexer;
    private final EventHiding eventHiding;
    private final EventFeaturing eventFeaturing;
    private final Admins admins;

    public AdminEventToolsController(ArtistIndexer artistIndexer, EventHiding eventHiding, EventFeaturing eventFeaturing,
                                     Admins admins) {
        this.artistIndexer = artistIndexer;
        this.eventHiding = eventHiding;
        this.eventFeaturing = eventFeaturing;
        this.admins = admins;
    }

    /** The featured events, newest first ({@code upcoming} false once over) */
    @GetMapping("/featured")
    public List<EventFeaturing.Featured> featured(Authentication authentication) {
        admins.requireEditor(authentication);
        return eventFeaturing.list();
    }

    public record Featuring(@Size(max = 200) String note) {
    }

    /** {@code PUT /api/admin/events/118537/featured {"note": "Tickets almost gone"}}: 409 with 3 featured already */
    @PutMapping("/{id}/featured")
    public EventFeaturing.Featured feature(@PathVariable long id, @Valid @RequestBody(required = false) Featuring featuring,
                                           Authentication authentication) {
        String admin = admins.requireEditor(authentication);
        return eventFeaturing.feature(id, featuring == null ? null : featuring.note(), admin);
    }

    @DeleteMapping("/{id}/featured")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfeature(@PathVariable long id, Authentication authentication) {
        eventFeaturing.unfeature(id, admins.requireEditor(authentication));
    }

    /** Reads the event's line-up right away instead of waiting for its turn: {@code {"artists": [...]}} */
    @PostMapping("/{id}/reread")
    public Map<String, List<String>> reread(@PathVariable long id, Authentication authentication) {
        admins.requireEditor(authentication);
        return Map.of("artists", artistIndexer.readNow(id));
    }

    @GetMapping("/hidden")
    public List<EventHiding.Hidden> hidden(Authentication authentication) {
        admins.requireEditor(authentication);
        return eventHiding.list();
    }

    public record Hiding(@Size(max = 300) String reason) {
    }

    /** {@code PUT /api/admin/events/118537/hidden {"reason": "Not psytrance"}} */
    @PutMapping("/{id}/hidden")
    public EventHiding.Hidden hide(@PathVariable long id, @Valid @RequestBody(required = false) Hiding hiding,
                                   Authentication authentication) {
        String admin = admins.requireEditor(authentication);
        return eventHiding.hide(id, hiding == null ? null : hiding.reason(), admin);
    }

    @DeleteMapping("/{id}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void show(@PathVariable long id, Authentication authentication) {
        eventHiding.show(id, admins.requireEditor(authentication));
    }
}
