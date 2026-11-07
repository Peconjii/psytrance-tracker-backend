package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.artists.ArtistDiscography;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.psytrance.psytrance_tracker_backend.util.Logins.usernameOrNull;

/** Artists' albums and festival sets: the map reads them, artists and festivals edit their own */
@RestController
public class DiscographyController {

    private final ArtistDiscography discography;

    public DiscographyController(ArtistDiscography discography) {
        this.discography = discography;
    }

    /** {@code artist slug -> country, since, albums} */
    @GetMapping("/api/map/discography")
    public Map<String, ArtistDiscography.Discography> all() {
        return discography.all();
    }

    /** Every set at a festival station of the map */
    @GetMapping("/api/map/sets")
    public List<ArtistDiscography.Set> sets() {
        return discography.sets();
    }

    @GetMapping("/api/artists/{slug}/discography")
    public ArtistDiscography.Discography of(@PathVariable String slug) {
        return discography.of(slug);
    }

    @PutMapping("/api/artists/{slug}/discography")
    public ArtistDiscography.Discography save(@PathVariable String slug, @RequestBody ArtistDiscography.Discography body,
                                              Authentication authentication) {
        return discography.save(slug, body, usernameOrNull(authentication));
    }

    @GetMapping("/api/festivals/sets")
    public List<ArtistDiscography.FestivalSet> setsAt(@RequestParam String id) {
        return discography.setsAt(id);
    }

    @PutMapping("/api/festivals/sets")
    public List<ArtistDiscography.FestivalSet> saveSetsAt(@RequestParam String id, @RequestBody List<ArtistDiscography.FestivalSet> sets,
                                                          Authentication authentication) {
        return discography.saveSetsAt(id, sets, usernameOrNull(authentication));
    }
}
