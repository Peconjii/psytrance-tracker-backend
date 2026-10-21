package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.AdminChangeDto;
import com.psytrance.psytrance_tracker_backend.service.MapPlacements;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The admin history: every change made in the admin panel, and undoing one. Admins only. */
@RestController
@RequestMapping("/api/admin/changes")
public class AdminChangeController {

    private final MapPlacements mapPlacements;
    private final Admins admins;

    public AdminChangeController(MapPlacements mapPlacements, Admins admins) {
        this.mapPlacements = mapPlacements;
        this.admins = admins;
    }

    /** {@code GET /api/admin/changes}: the latest 200 changes, newest first */
    @GetMapping
    public List<AdminChangeDto> history(Authentication authentication) {
        admins.requireAdmin(authentication);
        return mapPlacements.history();
    }

    /** {@code POST /api/admin/changes/12/undo}: puts back what change 12 replaced; returns change 12, now undone */
    @PostMapping("/{id}/undo")
    public AdminChangeDto undo(@PathVariable long id, Authentication authentication) {
        String admin = admins.requireAdmin(authentication);
        return mapPlacements.undo(id, admin);
    }
}
