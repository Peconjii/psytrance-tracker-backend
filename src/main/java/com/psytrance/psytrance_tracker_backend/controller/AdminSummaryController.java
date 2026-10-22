package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.service.AdminSummary;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The admin dashboard, and the number of things waiting for an admin (shown next to "Admin" in the menu) */
@RestController
@RequestMapping("/api/admin")
public class AdminSummaryController {

    private final AdminSummary adminSummary;
    private final Admins admins;

    public AdminSummaryController(AdminSummary adminSummary, Admins admins) {
        this.adminSummary = adminSummary;
        this.admins = admins;
    }

    @GetMapping("/dashboard")
    public AdminSummary.Dashboard dashboard(Authentication authentication) {
        admins.requireAdmin(authentication);
        return adminSummary.dashboard();
    }

    /** {@code {feedback, events, artistRequests, total}} */
    @GetMapping("/waiting")
    public AdminSummary.Waiting waiting(Authentication authentication) {
        admins.requireAdmin(authentication);
        return adminSummary.waiting();
    }
}
