package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.FestivalStats;
import com.psytrance.psytrance_tracker_backend.service.OwnerStats;
import com.psytrance.psytrance_tracker_backend.service.Stats;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Numbers for the artist, label and festival pages: gigs by country, genre and year, followers and their growth. Public. */
@RestController
public class StatsController {

    private final Stats stats;
    private final FestivalStats festivalStats;
    private final OwnerStats ownerStats;

    public StatsController(Stats stats, FestivalStats festivalStats, OwnerStats ownerStats) {
        this.stats = stats;
        this.festivalStats = festivalStats;
        this.ownerStats = ownerStats;
    }

    /** Example: {@code GET /api/artists/ajja/stats} */
    @GetMapping("/api/artists/{slug}/stats")
    public Stats.ArtistStats artist(@PathVariable String slug) {
        return stats.artist(slug);
    }

    /** Every label of the map with its crew size, upcoming gigs and followers, for the list of labels */
    @GetMapping("/api/labels/overview")
    public List<Stats.LabelSummary> labels() {
        return stats.labels();
    }

    /** Example: {@code GET /api/labels/stats?label=TIP Records}: a label of the map with its crew together */
    @GetMapping("/api/labels/stats")
    public Stats.LabelStats label(@RequestParam String label) {
        return stats.label(label);
    }

    /**
     * Example: {@code GET /api/festivals/stats?match=ozora&id=ozora}: the events named like a festival of the map, with
     * the editions set by hand for that festival id, and more
     */
    @GetMapping("/api/festivals/stats")
    public FestivalStats.Numbers festival(@RequestParam String match, @RequestParam(required = false) String id) {
        return festivalStats.festival(match, id);
    }

    // The owner's numbers. GET under /api/artists and /api/organizers is public in SecurityConfig, so the login is
    // checked here: 401 for guests, 403 for anyone but the owner and admins

    /** Example: {@code GET /api/artists/ajja/owner-stats}: per gig going, saved, reviews and the followers it reached */
    @GetMapping("/api/artists/{slug}/owner-stats")
    public OwnerStats.ArtistNumbers artistOwner(@PathVariable String slug, Authentication authentication) {
        return ownerStats.artist(slug, username(authentication));
    }

    /** Example: {@code GET /api/organizers/cosmic-circus/owner-stats}: per event going, saved, reviews and line-up reach */
    @GetMapping("/api/organizers/{slug}/owner-stats")
    public OwnerStats.OrganizerNumbers organizerOwner(@PathVariable String slug, Authentication authentication) {
        return ownerStats.organizer(slug, username(authentication));
    }

    private static String username(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken ? null : authentication.getName();
    }
}
