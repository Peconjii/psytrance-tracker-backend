package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.ArtistDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistSummary;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    /** Example: {@code GET /api/artists?search=astrix}. Without a search: the artists with the most upcoming events. */
    @GetMapping
    public List<ArtistSummary> getArtists(@RequestParam(required = false) String search) {
        return artistService.findArtists(search);
    }

    /** Example: {@code GET /api/artists/ace-ventura} */
    @GetMapping("/{slug}")
    public ArtistDto getArtist(@PathVariable String slug) {
        return artistService.findBySlug(slug);
    }
}
