package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.FestivalStats;
import com.psytrance.psytrance_tracker_backend.service.Stats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Numbers for the artist, label and festival pages: gigs by country, genre and year, followers and their growth. Public. */
@RestController
public class StatsController {

    private final Stats stats;
    private final FestivalStats festivalStats;

    public StatsController(Stats stats, FestivalStats festivalStats) {
        this.stats = stats;
        this.festivalStats = festivalStats;
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

    /** Example: {@code GET /api/festivals/stats?match=ozora}: the events named like a festival of the map, and more */
    @GetMapping("/api/festivals/stats")
    public FestivalStats.Numbers festival(@RequestParam String match) {
        return festivalStats.festival(match);
    }
}
