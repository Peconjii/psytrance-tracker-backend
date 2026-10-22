package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.OurEventDto;
import com.psytrance.psytrance_tracker_backend.service.OurEvents;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admins check the events added on our site: approve them onto the map, or reject (take down) them. */
@RestController
@RequestMapping("/api/admin/our-events")
public class AdminOurEventController {

    private final OurEvents ourEvents;
    private final Admins admins;

    public AdminOurEventController(OurEvents ourEvents, Admins admins) {
        this.ourEvents = ourEvents;
        this.admins = admins;
    }

    /** Example: {@code GET /api/admin/our-events?status=PENDING} (the default), APPROVED or REJECTED */
    @GetMapping
    public List<OurEventDto> list(@RequestParam(required = false) String status, Authentication authentication) {
        admins.requireEditor(authentication);
        return ourEvents.list(status);
    }

    @PostMapping("/{id}/approve")
    public OurEventDto approve(@PathVariable long id, Authentication authentication) {
        return ourEvents.approve(id, admins.requireEditor(authentication));
    }

    public record Rejection(@Size(max = 500) String reason) {
    }

    /** The reason is shown to the account that sent it */
    @PostMapping("/{id}/reject")
    public OurEventDto reject(@PathVariable long id, @Valid @RequestBody(required = false) Rejection rejection,
                              Authentication authentication) {
        String admin = admins.requireEditor(authentication);
        return ourEvents.reject(id, rejection == null ? null : rejection.reason(), admin);
    }
}
