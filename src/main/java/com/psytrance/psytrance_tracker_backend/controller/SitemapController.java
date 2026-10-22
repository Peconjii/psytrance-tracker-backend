package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.service.Sitemap;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** The sitemap for search engines; the frontend's /sitemap.xml is a rewrite to here (vercel.json) */
@RestController
public class SitemapController {

    private final Sitemap sitemap;

    public SitemapController(Sitemap sitemap) {
        this.sitemap = sitemap;
    }

    @GetMapping("/api/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                // Search engines and Vercel may keep it for a few hours: the pages don't change faster than that
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                .body(sitemap.xml());
    }
}
