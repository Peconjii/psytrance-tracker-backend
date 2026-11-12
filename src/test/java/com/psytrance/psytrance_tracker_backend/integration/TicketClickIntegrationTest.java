package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Clicks on an event's ticket button: guests count them, only admins read them */
class TicketClickIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Test
    void guestsCountClicksAndOnlyAdminsReadThem() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/events/987654321/ticket-click").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Click Test Festival\"}")).andExpect(status().isNoContent());
        }
        mockMvc.perform(post("/api/events/not-an-event/ticket-click")).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/ticket-clicks")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        String adminToken = login(ADMIN, PASSWORD);
        mockMvc.perform(get("/api/admin/ticket-clicks").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventId == '987654321')].clicks").value(3))
                .andExpect(jsonPath("$[?(@.eventId == '987654321')].today").value(3))
                .andExpect(jsonPath("$[?(@.eventId == '987654321')].eventName").value("Click Test Festival"));
    }
}
