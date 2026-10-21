package com.psytrance.psytrance_tracker_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code GET /api/health} for an outside cron job that keeps the free Render server awake, so the background jobs
 * keep running. It touches neither the database nor Goabase, so a ping every few minutes lets the Neon database
 * sleep between the jobs (its free quota counts the time it is awake).
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
