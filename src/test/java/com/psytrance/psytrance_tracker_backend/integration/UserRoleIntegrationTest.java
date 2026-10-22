package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Roles from the admin panel: an editor fixes the content only; a new admin may do everything */
class UserRoleIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test, from ADMIN_USERNAMES
    private static final String PASSWORD = "secret-password-123";

    private String adminToken;

    @BeforeEach
    void anAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
    }

    @Test
    void anEditorFixesTheContentButSeesNoPrivateParts() throws Exception {
        String editor = uniqueUsername();
        register(editor, PASSWORD);
        setRole(adminToken, editor, "\"EDITOR\"").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("EDITOR"));
        String token = login(editor, PASSWORD);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.editor").value(true))
                .andExpect(jsonPath("$.admin").value(false));
        // The content
        mockMvc.perform(get("/api/admin/events/hidden").header("Authorization", bearer(token))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/artists").header("Authorization", bearer(token))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/our-events").header("Authorization", bearer(token))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/reviews").header("Authorization", bearer(token))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(token))).andExpect(status().isOk());
        // Not the private parts, the requests, the history or the roles
        mockMvc.perform(get("/api/admin/feedback").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/claims").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        setRole(token, editor, "\"ADMIN\"").andExpect(status().isForbidden());

        // Taken away: a user again, in the history
        setRole(adminToken, editor, "null").andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/events/hidden").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].kind").value("USER_ROLE"))
                .andExpect(jsonPath("$[0].target").value(editor));
    }

    @Test
    void aNewAdminMayDoEverythingButTheServerAdminsStay() throws Exception {
        String admin = uniqueUsername();
        register(admin, PASSWORD);
        setRole(adminToken, admin, "\"ADMIN\"").andExpect(status().isOk());
        String token = login(admin, PASSWORD);

        mockMvc.perform(get("/api/admin/users").param("team", "true").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[?(@.username == '%s')].role".formatted(admin)).value("ADMIN"))
                .andExpect(jsonPath("$.users[?(@.username == '%s')].roleOnServer".formatted(ADMIN)).value(true));
        // The admin from ADMIN_USERNAMES can't be changed here, and nobody changes their own role
        setRole(token, ADMIN, "null").andExpect(status().isConflict());
        setRole(token, admin, "null").andExpect(status().isConflict());
        setRole(token, admin, "\"OWNER\"").andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions setRole(String token, String username, String role) throws Exception {
        return mockMvc.perform(put("/api/admin/users/{username}/role", username).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\": " + role + "}"));
    }
}
