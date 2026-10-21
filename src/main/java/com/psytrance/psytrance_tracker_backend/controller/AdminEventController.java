package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.AdminEventGenresDto;
import com.psytrance.psytrance_tracker_backend.service.EventGenreAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The admin panel's event genres: set the genres of an event by hand, or hand it back to the line-up. Admins only. */
@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private final EventGenreAdmin eventGenreAdmin;
    private final Admins admins;

    public AdminEventController(EventGenreAdmin eventGenreAdmin, Admins admins) {
        this.eventGenreAdmin = eventGenreAdmin;
        this.admins = admins;
    }

    /** {@code GET /api/admin/events/118537/genres}: the event's genres and whether they were set by hand */
    @GetMapping("/{id}/genres")
    public AdminEventGenresDto get(@PathVariable long id, Authentication authentication) {
        admins.requireAdmin(authentication);
        return eventGenreAdmin.get(id);
    }

    /** {@code PUT /api/admin/events/118537/genres {"genres": ["forest", "twilight"]}}; [] goes back to the line-up */
    @PutMapping("/{id}/genres")
    public AdminEventGenresDto set(@PathVariable long id, @Valid @RequestBody GenreChange change, Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        return eventGenreAdmin.setGenres(id, change.genres(), admin);
    }

    public record GenreChange(@NotNull @Size(max = 10) List<String> genres) {
    }
}
