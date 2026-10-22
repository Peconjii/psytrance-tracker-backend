package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import com.psytrance.psytrance_tracker_backend.service.Organizers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The numbers only owners and admins see: per gig or event who is going, saved it and was reached */
class OwnerStatsIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(998_000);
    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistService artistService;

    @Autowired
    private Organizers organizers;

    @Test
    void theOwnersSeeWhoIsGoingAndWhoWasReached() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        String adminToken = login(ADMIN, PASSWORD);
        String unique = uniqueLetters();
        String artist = "Owner Stats Act " + unique;
        String artistSlug = "owner-stats-act-" + unique;
        long eventId = NEXT_EVENT_ID.getAndIncrement();
        LocalDate day = LocalDate.now().plusDays(8);
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(eventId, "Owner Night " + unique,
                day + "T22:00:00+02:00", null, "Club", "Scheduled", "Austria", "Vienna", 48.2, 16.4, null, null)));
        artistService.saveLineUp(eventId, List.of(artist), Instant.now());
        String organizer = organizers.record("Owner Crew " + unique, null);
        artistService.saveSummary(new EventDto(eventId, "Owner Night " + unique, "Vienna", "Austria", "Club", "Scheduled",
                day, null, null, null, 48.2, 16.4), organizer);

        // A fan follows the artist, then says they're going
        String fan = uniqueUsername();
        register(fan, PASSWORD);
        String fanToken = login(fan, PASSWORD);
        mockMvc.perform(put("/api/me/follows/{slug}", artistSlug).header("Authorization", bearer(fanToken))).andExpect(status().isOk());
        mockMvc.perform(put("/api/events/{id}/going", eventId).header("Authorization", bearer(fanToken))).andExpect(status().isOk());

        mockMvc.perform(get("/api/artists/{slug}/owner-stats", artistSlug).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followers").value(1))
                .andExpect(jsonPath("$.gigs[0].eventId").value((int) eventId))
                .andExpect(jsonPath("$.gigs[0].going").value(1))
                .andExpect(jsonPath("$.gigs[0].reach").value(1));
        mockMvc.perform(get("/api/organizers/{slug}/owner-stats", organizer).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].going").value(1))
                .andExpect(jsonPath("$.events[0].reach").value(1))
                .andExpect(jsonPath("$.totals.going").value(1));

        mockMvc.perform(get("/api/artists/{slug}/owner-stats", artistSlug).header("Authorization", bearer(fanToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizers/{slug}/owner-stats", organizer)).andExpect(status().isUnauthorized());
    }
}
