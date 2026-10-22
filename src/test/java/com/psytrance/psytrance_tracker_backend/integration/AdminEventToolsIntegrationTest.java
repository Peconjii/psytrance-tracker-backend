package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin's tools for one event: read its line-up again now, hide it, show it again */
class AdminEventToolsIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(993_000);
    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    private String adminToken;
    private String unique;
    private long eventId;
    private String day;

    @BeforeEach
    void anEvent() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
        unique = uniqueLetters();
        eventId = NEXT_EVENT_ID.getAndIncrement();
        day = LocalDate.now().plusDays(9) + "T22:00:00+02:00";
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(eventId, "Tools Night " + unique, day,
                null, "Club", "Scheduled", "Austria", "Vienna", 48.2, 16.4, null, null)));
        lineUp("First Act " + unique);
    }

    @Test
    void theLineUpIsReadAgainRightAway() throws Exception {
        mockMvc.perform(post("/api/admin/events/{id}/reread", eventId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artists").value(hasItem("First Act " + unique)));

        lineUp("First Act " + unique + "\nLate Addition " + unique);
        mockMvc.perform(post("/api/admin/events/{id}/reread", eventId).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.artists").value(hasItem("Late Addition " + unique)));
        mockMvc.perform(get("/api/artists/{slug}", "late-addition-" + unique))
                .andExpect(status().isOk());
    }

    @Test
    void aHiddenEventLeavesTheSiteUntilShownAgain() throws Exception {
        mockMvc.perform(put("/api/admin/events/{id}/hidden", eventId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Not psytrance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tools Night " + unique));
        mockMvc.perform(get("/api/events").param("search", unique))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) eventId))));
        mockMvc.perform(get("/api/events/{id}/details", eventId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/events/hidden").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[*].eventId").value(hasItem((int) eventId)));

        mockMvc.perform(delete("/api/admin/events/{id}/hidden", eventId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/events").param("search", unique))
                .andExpect(jsonPath("$.content[*].id").value(hasItem((int) eventId)));
    }

    private void lineUp(String text) {
        when(goabaseClient.fetchParty(eventId)).thenReturn(Optional.of(new GoabasePartyDetails(eventId, "Tools Night " + unique,
                day, null, "Club", "Scheduled", "Austria", "Vienna", 48.2, 16.4, null, null, null, text, "", "", "", "", null)));
    }
}
