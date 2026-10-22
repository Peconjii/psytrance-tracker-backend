package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.AdminArtistDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistProfileDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistProfileRequest;
import com.psytrance.psytrance_tracker_backend.service.ArtistProfiles;
import com.psytrance.psytrance_tracker_backend.service.ArtistCleanup;
import com.psytrance.psytrance_tracker_backend.service.MapPlacements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The admin panel's artist genres: find an artist and place them in the right map genres. Admins only. */
@RestController
@RequestMapping("/api/admin/artists")
public class AdminArtistController {

    private final MapPlacements mapPlacements;
    private final Admins admins;
    private final ArtistCleanup artistCleanup;
    private final ArtistProfiles artistProfiles;

    public AdminArtistController(MapPlacements mapPlacements, Admins admins, ArtistCleanup artistCleanup,
                                 ArtistProfiles artistProfiles) {
        this.mapPlacements = mapPlacements;
        this.admins = admins;
        this.artistCleanup = artistCleanup;
        this.artistProfiles = artistProfiles;
    }

    /** {@code GET /api/admin/artists/ajja/profile}: the profile to edit; every field null when there is none yet */
    @GetMapping("/{slug}/profile")
    public ArtistProfileDto getProfile(@PathVariable String slug, Authentication authentication) {
        admins.requireAdmin(authentication);
        return artistProfiles.get(slug).orElse(new ArtistProfileDto(null, null, null, null, null, null, null));
    }

    /** {@code PUT /api/admin/artists/ajja/profile}: writes the whole profile; blank fields clear them */
    @PutMapping("/{slug}/profile")
    public ArtistProfileDto setProfile(@PathVariable String slug, @Valid @RequestBody ArtistProfileRequest profile,
                                       Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        return artistProfiles.save(slug, profile, admin);
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

    /** {@code POST /api/admin/artists/hidden {"name": "Coming Soon", "hidden": true}}: not an artist, skipped from now on */
    @PostMapping("/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setHidden(@Valid @RequestBody HiddenChange change, Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        artistCleanup.setHidden(change.name(), change.hidden(), admin);
    }

    /** {@code POST /api/admin/artists/merge {"from": "AJJA", "into": "Ajja Experience"}}: one artist, two spellings */
    @PostMapping("/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void merge(@Valid @RequestBody Merge merge, Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        artistCleanup.merge(merge.from(), merge.into(), admin);
    }

    public record HiddenChange(@NotBlank @Size(max = 100) String name, boolean hidden) {
    }

    public record Merge(@NotBlank @Size(max = 100) String from, @NotBlank @Size(max = 100) String into) {
    }

    public record GenreChange(@NotBlank @Size(max = 100) String name, @NotNull @Size(max = 10) List<String> genres) {
    }
}
