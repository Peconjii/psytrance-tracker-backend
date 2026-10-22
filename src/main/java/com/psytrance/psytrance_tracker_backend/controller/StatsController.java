package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.Stats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Numbers for the artist and label pages: gigs by country, genre and year, followers and their growth. Public. */
@RestController
public class StatsController {

    private final Stats stats;

    public StatsController(Stats stats) {
        this.stats = stats;
    }

    /** Example: {@code GET /api/artists/ajja/stats} */
    @GetMapping("/api/artists/{slug}/stats")
    public Stats.ArtistStats artist(@PathVariable String slug) {
        return stats.artist(slug);
    }

    /** Example: {@code GET /api/labels/stats?label=TIP Records}: a label of the map with its crew together */
    @GetMapping("/api/labels/stats")
    public Stats.LabelStats label(@RequestParam String label) {
        return stats.label(label);
    }
}
