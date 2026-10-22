package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.OrganizerEditing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admins link an account to an organizer, who can then write their page and their events' timetables */
@RestController
@RequestMapping("/api/admin/organizers")
public class AdminOrganizerController {

    private final OrganizerEditing organizerEditing;
    private final Admins admins;

    public AdminOrganizerController(OrganizerEditing organizerEditing, Admins admins) {
        this.organizerEditing = organizerEditing;
        this.admins = admins;
    }

    /** {@code PUT /api/admin/organizers/cosmic-circus/owner {"username": "cosmic_crew"}} */
    @PutMapping("/{slug}/owner")
    public Map<String, String> setOwner(@PathVariable String slug, @Valid @RequestBody OwnerChange change,
                                        Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        return Map.of("owner", organizerEditing.setOwner(slug, change.username(), admin));
    }

    @DeleteMapping("/{slug}/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeOwner(@PathVariable String slug, Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        organizerEditing.removeOwner(slug, admin);
    }

    record OwnerChange(@NotBlank @Size(max = 255) String username) {
    }
}
