package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.AdminUsersDto;
import com.psytrance.psytrance_tracker_backend.service.account.AdminUsers;
import com.psytrance.psytrance_tracker_backend.service.account.UserDeletion;
import com.psytrance.psytrance_tracker_backend.service.account.UserRoles;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Admin → Users: who has an account and what they use, and the roles admins give */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUsers adminUsers;
    private final UserRoles userRoles;
    private final Admins admins;
    private final UserDeletion userDeletion;

    public AdminUserController(AdminUsers adminUsers, UserRoles userRoles, Admins admins, UserDeletion userDeletion) {
        this.adminUsers = adminUsers;
        this.userRoles = userRoles;
        this.admins = admins;
        this.userDeletion = userDeletion;
    }

    /** Example: {@code GET /api/admin/users?search=gmail}: name or email contains it; {@code ?team=true}: admins and editors */
    @GetMapping
    public AdminUsersDto list(@RequestParam(required = false) String search, @RequestParam(defaultValue = "false") boolean team,
                              Authentication authentication) {
        admins.requireAdmin(authentication);
        return adminUsers.list(search, team);
    }

    public record RoleChange(String role) {
    }

    /** {@code PUT /api/admin/users/ajja/role {"role": "EDITOR"}}; {"role": null} makes them a user again */
    @PutMapping("/{username}/role")
    public Map<String, String> setRole(@PathVariable String username, @RequestBody RoleChange change, Authentication authentication) {
        String role = userRoles.set(username, change.role(), admins.requireAdmin(authentication));
        Map<String, String> result = new HashMap<>();
        result.put("username", username);
        result.put("role", role);
        return result;
    }

    /** {@code DELETE /api/admin/users/someone}: removes the account (see UserDeletion for what stays) */
    @DeleteMapping("/{username}")
    public Map<String, String> delete(@PathVariable String username, Authentication authentication) {
        userDeletion.delete(username, admins.requireAdmin(authentication));
        return Map.of("deleted", username);
    }
}
