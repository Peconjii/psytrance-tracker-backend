package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.AdminArtistDto;
import com.psytrance.psytrance_tracker_backend.service.MapPlacements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The admin panel's artist genres: find an artist and place them in the right map genres. Admins only. */
@RestController
@RequestMapping("/api/admin/artists")
public class AdminArtistController {

    private final MapPlacements mapPlacements;
    private final Admins admins;

    public AdminArtistController(MapPlacements mapPlacements, Admins admins) {
        this.mapPlacements = mapPlacements;
        this.admins = admins;
    }

    /** Example: {@code GET /api/admin/artists?search=fungus}: map and line-up artists with their genres */
    @GetMapping
    public List<AdminArtistDto> search(@RequestParam(defaultValue = "") String search, Authentication authentication) {
        admins.requireAdmin(authentication);
        return mapPlacements.search(search);
    }

    /** Example: {@code PUT /api/admin/artists/genres {"name": "Fungus Funk", "genres": ["night"]}}; [] takes them off the map */
    @PutMapping("/genres")
    public AdminArtistDto setGenres(@Valid @RequestBody GenreChange change, Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        return mapPlacements.setGenres(change.name(), change.genres(), admin);
    }

    public record GenreChange(@NotBlank @Size(max = 100) String name, @NotNull @Size(max = 10) List<String> genres) {
    }
}
