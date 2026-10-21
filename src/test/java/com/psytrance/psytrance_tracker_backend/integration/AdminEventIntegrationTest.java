package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.service.ArtistIndexer;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
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

/** Admins set the genres of an event by hand; the artist index leaves them alone until they hand it back. */
class AdminEventIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin";
    private static final String PASSWORD = "secret-password-123";
    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(700_000);

    @Autowired
    private ArtistIndexer artistIndexer;

    @Autowired
    private LineupScanRepository lineupScanRepository;

    private String adminToken;
    private long eventId;

    @BeforeEach
    void eventWithAForestLineUp() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);

        eventId = NEXT_EVENT_ID.getAndIncrement();
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(eventId, "Hand Set Gathering",
                LocalDate.now().plusDays(2) + "T22:00:00+02:00", null, "Open Air", "Scheduled", "Serbia", "Novi Sad",
                45.25, 19.84, null, null)));
        when(goabaseClient.fetchParty(eventId)).thenReturn(Optional.of(new GoabasePartyDetails(eventId, "Hand Set Gathering",
                LocalDate.now().plusDays(2) + "T22:00:00+02:00", null, "Open Air", "Scheduled", "Serbia", "Novi Sad",
                45.25, 19.84, null, null, null, "Derango\r\nMegalopsy", "", "", "", null, null)));
        artistIndexer.indexNextBatch();
    }

    @Test
    void handSetGenresWinOverTheLineUpUntilHandedBack() throws Exception {
        genres().andExpect(jsonPath("$.manual").value(false))
                .andExpect(jsonPath("$.genres[0].genreId").value("forest"));

        setGenres("[\"twilight\", \"night\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manual").value(true))
                .andExpect(jsonPath("$.genres.length()").value(2))
                .andExpect(jsonPath("$.genres[0].source").value("MANUAL"))
                .andExpect(jsonPath("$.genres[0].share").value(0.5));

        // Reading the line-up again doesn't touch hand-set genres
        rereadSoon();
        artistIndexer.indexNextBatch();
        genres().andExpect(jsonPath("$.manual").value(true))
                .andExpect(jsonPath("$.genres[0].genreId").value("twilight"));

        // Handed back: the line-up decides again at the next read
        setGenres("[]").andExpect(jsonPath("$.manual").value(false));
        artistIndexer.indexNextBatch();
        genres().andExpect(jsonPath("$.genres[0].genreId").value("forest"))
                .andExpect(jsonPath("$.genres[0].source").value("ARTIST"));
    }

    @Test
    void anEventChangeIsInTheHistoryAndCanBeUndone() throws Exception {
        setGenres("[\"goa\"]").andExpect(status().isOk());

        String history = mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].kind").value("EVENT_GENRES"))
                .andExpect(jsonPath("$[0].target").value(eventId + " · Hand Set Gathering"))
                .andExpect(jsonPath("$[0].before").value(""))
                .andExpect(jsonPath("$[0].after").value("goa"))
                .andReturn().getResponse().getContentAsString();
        long changeId = ((Number) JsonPath.read(history, "$[0].id")).longValue();

        mockMvc.perform(post("/api/admin/changes/{id}/undo", changeId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        genres().andExpect(jsonPath("$.manual").value(false));
    }

    @Test
    void unknownGenresAndNonAdminsAreRefused() throws Exception {
        setGenres("[\"polka\"]").andExpect(status().isBadRequest());

        String username = uniqueUsername();
        register(username, PASSWORD);
        mockMvc.perform(put("/api/admin/events/{id}/genres", eventId).header("Authorization", bearer(login(username, PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"genres\": []}"))
                .andExpect(status().isForbidden());
    }

    // Marks the line-up as read long ago, so the next batch reads it first
    private void rereadSoon() {
        lineupScanRepository.save(new LineupScan(eventId, Instant.EPOCH));
    }

    private ResultActions genres() throws Exception {
        return mockMvc.perform(get("/api/admin/events/{id}/genres", eventId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private ResultActions setGenres(String genres) throws Exception {
        return mockMvc.perform(put("/api/admin/events/{id}/genres", eventId).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"genres\": %s}".formatted(genres)));
    }
}
