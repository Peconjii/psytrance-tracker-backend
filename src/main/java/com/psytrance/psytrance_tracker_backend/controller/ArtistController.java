package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.ArtistChangeRequest;
import com.psytrance.psytrance_tracker_backend.dto.ArtistDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistEditDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistListItem;
import com.psytrance.psytrance_tracker_backend.dto.ArtistListsRequest;
import com.psytrance.psytrance_tracker_backend.dto.ArtistProfileDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistProfileRequest;
import com.psytrance.psytrance_tracker_backend.dto.ArtistRequestDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistEditing;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import com.psytrance.psytrance_tracker_backend.service.FreshPages;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
import java.util.Map;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private final ArtistService artistService;
    private final ArtistEditing artistEditing;
    private final FreshPages freshPages;

    public ArtistController(ArtistService artistService, ArtistEditing artistEditing, FreshPages freshPages) {
        this.artistService = artistService;
        this.artistEditing = artistEditing;
        this.freshPages = freshPages;
    }

    /** Example: {@code GET /api/artists?search=astrix}. Without a search: the artists with the most upcoming events. */
    @GetMapping
    public List<ArtistListItem> getArtists(@RequestParam(required = false) String search) {
        return artistService.findArtists(search);
    }

    /** Artists with their own page and no upcoming event yet, newest first: {@code GET /api/artists/new-pages?search=lep} */
    @GetMapping("/new-pages")
    public List<FreshPages.Page> getNewPages(@RequestParam(required = false) String search) {
        return freshPages.artists(search);
    }

    /** Example: {@code GET /api/artists/ace-ventura} */
    @GetMapping("/{slug}")
    public ArtistDto getArtist(@PathVariable String slug) {
        return artistService.findBySlug(slug);
    }

    // Editing an artist: their owner (an account an admin linked to them) and admins. GET is public in SecurityConfig,
    // so the editor checks the login itself (401 for guests, 403 for other users).

    /** Everything the profile editor shows */
    @GetMapping("/{slug}/edit")
    public ArtistEditDto getEdit(@PathVariable String slug, Authentication authentication) {
        return artistEditing.edit(slug, username(authentication));
    }

    /** Photo, bio, links and booking: the whole profile, blank fields clear them */
    @PutMapping("/{slug}/profile")
    public ArtistProfileDto setProfile(@PathVariable String slug, @Valid @RequestBody ArtistProfileRequest profile,
                                       Authentication authentication) {
        return artistEditing.saveProfile(slug, profile, username(authentication));
    }

    @PutMapping("/{slug}/tracks")
    public List<ArtistListsRequest.Track> setTracks(@PathVariable String slug, @Valid @RequestBody ArtistListsRequest.Tracks tracks,
                                                    Authentication authentication) {
        return artistEditing.saveTracks(slug, tracks, username(authentication));
    }

    @PutMapping("/{slug}/festivals")
    public List<ArtistListsRequest.Festival> setFestivals(@PathVariable String slug,
                                                          @Valid @RequestBody ArtistListsRequest.Festivals festivals,
                                                          Authentication authentication) {
        return artistEditing.saveFestivals(slug, festivals, username(authentication));
    }

    /** {@code PUT /api/artists/ajja/role {"role": "DJ"}}: DJ, PRODUCER or LIVE; null hands it back to the guess */
    @PutMapping("/{slug}/role")
    public Map<String, String> setRole(@PathVariable String slug, @RequestBody RoleChange change, Authentication authentication) {
        String role = artistEditing.setRole(slug, change.role(), username(authentication));
        return role == null ? Map.of() : Map.of("role", role);
    }

    record RoleChange(String role) {
    }

    /** Other map genres or another name on the page: an admin decides (an admin's own request applies at once) */
    @PostMapping("/{slug}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ArtistRequestDto request(@PathVariable String slug, @Valid @RequestBody ArtistChangeRequest change,
                                    Authentication authentication) {
        return artistEditing.request(slug, change, username(authentication));
    }

    private static String username(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken ? null : authentication.getName();
    }
}
