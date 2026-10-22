package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.AdminUsersDto;
import com.psytrance.psytrance_tracker_backend.service.AdminUsers;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin → Users: who has an account and what they use */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUsers adminUsers;
    private final Admins admins;

    public AdminUserController(AdminUsers adminUsers, Admins admins) {
        this.adminUsers = adminUsers;
        this.admins = admins;
    }

    /** Example: {@code GET /api/admin/users?search=gmail}: name or email contains it */
    @GetMapping
    public AdminUsersDto list(@RequestParam(required = false) String search, Authentication authentication) {
        admins.requireAdmin(authentication);
        return adminUsers.list(search);
    }
}
