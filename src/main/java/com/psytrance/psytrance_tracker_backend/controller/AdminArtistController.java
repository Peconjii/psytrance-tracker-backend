package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.AdminArtistDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistRequestDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistEditing;
import com.psytrance.psytrance_tracker_backend.service.Organizers;
import com.psytrance.psytrance_tracker_backend.service.ArtistCleanup;
import com.psytrance.psytrance_tracker_backend.service.MapPlacements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The admin panel's artist genres: find an artist and place them in the right map genres. Admins only. */
@RestController
@RequestMapping("/api/admin/artists")
public class AdminArtistController {

    private final MapPlacements mapPlacements;
    private final Admins admins;
    private final ArtistCleanup artistCleanup;
    private final ArtistEditing artistEditing;
    private final Organizers organizers;

    public AdminArtistController(MapPlacements mapPlacements, Admins admins, ArtistCleanup artistCleanup,
                                 ArtistEditing artistEditing, Organizers organizers) {
        this.mapPlacements = mapPlacements;
        this.admins = admins;
        this.artistCleanup = artistCleanup;
        this.artistEditing = artistEditing;
        this.organizers = organizers;
    }

    /** {@code PUT /api/admin/artists/ajja/owner {"username": "ajja_official"}}: that account may edit the profile */
    @PutMapping("/{slug}/owner")
    public Map<String, String> setOwner(@PathVariable String slug, @Valid @RequestBody OwnerChange change,
                                        Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        return Map.of("owner", artistEditing.setOwner(slug, change.username(), admin));
    }

    @DeleteMapping("/{slug}/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeOwner(@PathVariable String slug, Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        artistEditing.removeOwner(slug, admin);
    }

    /** {@code GET /api/admin/artists/requests}: genre and name requests of artists' owners, waiting for an admin */
    @GetMapping("/requests")
    public List<ArtistRequestDto> pendingRequests(Authentication authentication) {
        admins.requireAdmin(authentication);
        return artistEditing.pendingRequests();
    }

    /** {@code POST /api/admin/artists/requests/7 {"approve": true}}: applies or rejects the request */
    @PostMapping("/requests/{id}")
    public ArtistRequestDto decide(@PathVariable long id, @Valid @RequestBody Decision decision, Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        return artistEditing.decide(id, decision.approve(), admin);
    }

    record OwnerChange(@NotBlank @Size(max = 255) String username) {
    }

    /**
     * {@code POST /api/admin/artists/organizer {"name": "Cosmic Circus"}}: a line-up name that is really the organizer.
     * It is hidden from the artists like "not an artist" (undo in the history) and gets an organizer page.
     */
    @PostMapping("/organizer")
    public Map<String, String> markAsOrganizer(@Valid @RequestBody HiddenName change, Authentication authentication) {
        String admin = admins.requireEditor(authentication);
        artistCleanup.setHidden(change.name(), true, admin);
        String slug = organizers.record(change.name(), null);
        return slug == null ? Map.of() : Map.of("organizer", slug);
    }

    record HiddenName(@NotBlank @Size(max = 100) String name) {
    }

    record Decision(@NotNull Boolean approve) {
    }

    /** Example: {@code GET /api/admin/artists?search=fungus}: map and line-up artists with their genres */
    @GetMapping
    public List<AdminArtistDto> search(@RequestParam(defaultValue = "") String search, Authentication authentication) {
        admins.requireEditor(authentication);
        return mapPlacements.search(search);
    }

    /** Example: {@code PUT /api/admin/artists/genres {"name": "Fungus Funk", "genres": ["night"]}}; [] takes them off the map */
    @PutMapping("/genres")
    public AdminArtistDto setGenres(@Valid @RequestBody GenreChange change, Authentication authentication) {
        String admin = admins.requireEditor(authentication);
        return mapPlacements.setGenres(change.name(), change.genres(), admin);
    }

    /** {@code POST /api/admin/artists/hidden {"name": "Coming Soon", "hidden": true}}: not an artist, skipped from now on */
    @PostMapping("/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setHidden(@Valid @RequestBody HiddenChange change, Authentication authentication) {
        String admin = admins.requireEditor(authentication);
        artistCleanup.setHidden(change.name(), change.hidden(), admin);
    }

    /** {@code POST /api/admin/artists/merge {"from": "AJJA", "into": "Ajja Experience"}}: one artist, two spellings */
    @PostMapping("/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void merge(@Valid @RequestBody Merge merge, Authentication authentication) {
        String admin = admins.requireEditor(authentication);
        artistCleanup.merge(merge.from(), merge.into(), admin);
    }

    public record HiddenChange(@NotBlank @Size(max = 100) String name, boolean hidden) {
    }

    public record Merge(@NotBlank @Size(max = 100) String from, @NotBlank @Size(max = 100) String into) {
    }

    public record GenreChange(@NotBlank @Size(max = 100) String name, @NotNull @Size(max = 10) List<String> genres) {
    }
}
