package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin dashboard and the count next to "Admin" in the menu */
class AdminSummaryIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Test
    void newFeedbackIsCountedAsWaiting() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        String adminToken = login(ADMIN, PASSWORD);
        mockMvc.perform(post("/api/feedback").contentType(MediaType.APPLICATION_JSON).content("""
                {"kind": "IDEA", "message": "A dashboard would be nice"}
                """)).andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/admin/waiting").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.waiting.feedback").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.mostFollowed").isArray());
    }
}
