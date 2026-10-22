package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.service.ArtistIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An organizer writing their page and the timetables of their own events, once an admin linked their account. */
class OrganizerOwnerIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(990_000);
    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistIndexer artistIndexer;

    private String adminToken;
    private String ownerToken;
    private String organizerSlug;
    private long theirEvent;
    private long otherEvent;

    @BeforeEach
    void anOrganizerWithAnOwner() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);

        String unique = uniqueLetters();
        organizerSlug = "owners-crew-" + unique;
        theirEvent = NEXT_EVENT_ID.getAndIncrement();
        otherEvent = NEXT_EVENT_ID.getAndIncrement();
        String day = LocalDate.now().plusDays(12) + "T22:00:00+02:00";
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(
                new GoabaseParty(theirEvent, "Their Night", day, null, "Club", "Scheduled", "Austria", "Vienna", 48.2, 16.4, null, null),
                new GoabaseParty(otherEvent, "Other Night", day, null, "Club", "Scheduled", "Austria", "Graz", 47.1, 15.4, null, null)));
        when(goabaseClient.fetchParty(theirEvent)).thenReturn(Optional.of(details(theirEvent, day, "Owners Crew " + unique)));
        when(goabaseClient.fetchParty(otherEvent)).thenReturn(Optional.of(details(otherEvent, day, "Somebody Else " + unique)));
        artistIndexer.indexNextBatch();

        String owner = uniqueUsername();
        register(owner, PASSWORD);
        ownerToken = login(owner, PASSWORD);
        mockMvc.perform(put("/api/admin/organizers/{slug}/owner", organizerSlug).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\": \"%s\"}".formatted(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void theOwnerWritesTheirPageAndTheirEventsTimetable() throws Exception {
        mockMvc.perform(get("/api/me/organizers").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$[0].slug").value(organizerSlug));
        mockMvc.perform(get("/api/organizers/{slug}/edit", organizerSlug).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].id").value(theirEvent));

        mockMvc.perform(put("/api/organizers/{slug}/profile", organizerSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\": \"Forest nights since 2010.\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/organizers/{slug}", organizerSlug))
                .andExpect(jsonPath("$.profile.bio").value("Forest nights since 2010."));

        String sets = """
                {"sets": [{"stage": "Main", "artist": "Ajja", "startsAt": "%sT23:00:00", "endsAt": null, "genreId": "twilight"}]}
                """.formatted(LocalDate.now().plusDays(12));
        mockMvc.perform(put("/api/organizers/{slug}/events/{id}/timetable", organizerSlug, theirEvent)
                        .header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON).content(sets))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/{id}/timetable", theirEvent))
                .andExpect(jsonPath("$.manual").value(true))
                .andExpect(jsonPath("$.sets[0].artist").value("Ajja"));

        // Another organizer's event is not theirs to edit
        mockMvc.perform(put("/api/organizers/{slug}/events/{id}/timetable", organizerSlug, otherEvent)
                        .header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON).content(sets))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheOwnerAndAdminsMayEdit() throws Exception {
        String stranger = uniqueUsername();
        register(stranger, PASSWORD);
        mockMvc.perform(put("/api/organizers/{slug}/profile", organizerSlug).header("Authorization", bearer(login(stranger, PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizers/{slug}/edit", organizerSlug))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/organizers/{slug}/profile", organizerSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"websiteUrl\": \"http://plain.example\"}"))
                .andExpect(status().isBadRequest());
    }

    private static GoabasePartyDetails details(long id, String day, String organizer) {
        return new GoabasePartyDetails(id, "Night " + id, day, null, "Club", "Scheduled", "Austria", "Vienna", 48.2, 16.4,
                null, null, null, "Ace Ventura", "", "", "", organizer, null);
    }
}
