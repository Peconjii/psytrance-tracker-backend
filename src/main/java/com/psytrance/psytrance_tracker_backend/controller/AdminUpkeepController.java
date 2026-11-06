package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.site.Upkeep;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin -> Upkeep: dead key-track videos and probable duplicate artists; admins only */
@RestController
@RequestMapping("/api/admin/upkeep")
public class AdminUpkeepController {

    private final Upkeep upkeep;
    private final Admins admins;

    public AdminUpkeepController(Upkeep upkeep, Admins admins) {
        this.upkeep = upkeep;
        this.admins = admins;
    }

    /** The last daily check; {@code null} body until the first one ran */
    @GetMapping("/dead-tracks")
    public Upkeep.DeadTracks deadTracks(Authentication authentication) {
        admins.requireAdmin(authentication);
        return upkeep.deadTracks();
    }

    /** Checks all key track videos now (takes about a minute) */
    @PostMapping("/dead-tracks")
    public Upkeep.DeadTracks checkNow(Authentication authentication) {
        admins.requireAdmin(authentication);
        return upkeep.checkTracks();
    }

    @GetMapping("/duplicate-artists")
    public List<Upkeep.DuplicateArtists> duplicateArtists(Authentication authentication) {
        admins.requireAdmin(authentication);
        return upkeep.duplicateArtists();
    }
}
