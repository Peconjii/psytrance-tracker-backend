package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.SiteSearch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The site's search box (public) */
@RestController
public class SearchController {

    private final SiteSearch siteSearch;

    public SearchController(SiteSearch siteSearch) {
        this.siteSearch = siteSearch;
    }

    /** Example: {@code GET /api/search?q=ajja}: artists, organizers, upcoming events, labels and festivals */
    @GetMapping("/api/search")
    public SiteSearch.Results search(@RequestParam String q) {
        return siteSearch.search(q);
    }
}
