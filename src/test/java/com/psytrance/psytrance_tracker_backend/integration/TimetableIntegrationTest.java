package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import com.psytrance.psytrance_tracker_backend.service.ArtistIndexer;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Timetables read from set times in the line-up, corrected by an admin, and handed back to the line-up. */
class TimetableIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin";
    private static final String PASSWORD = "secret-password-123";
    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(500_000);

    @Autowired
    private ArtistIndexer artistIndexer;

    @Autowired
    private LineupScanRepository lineupScanRepository;

    private String adminToken;
    private long eventId;
    private String day;
    private String nextDay;

    @BeforeEach
    void eventWithSetTimes() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);

        eventId = NEXT_EVENT_ID.getAndIncrement();
        day = LocalDate.now().plusDays(4).toString();
        nextDay = LocalDate.now().plusDays(5).toString();
        String start = day + "T22:00:00+02:00";
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(eventId, "Timed Night", start, null,
                "Club", "Scheduled", "Serbia", "Beograd", 44.8, 20.4, null, null)));
        when(goabaseClient.fetchParty(eventId)).thenReturn(Optional.of(new GoabasePartyDetails(eventId, "Timed Night", start,
                null, "Club", "Scheduled", "Serbia", "Beograd", 44.8, 20.4, null, null, null,
                "MAIN FLOOR\r\n22:00 - 00:00 | Derango\r\n00:00 - 02:00 | Megalopsy", "", "", "", null, null)));
        artistIndexer.indexNextBatch();
    }

    @Test
    void theLineUpsSetTimesArePublicAndItsArtistsCount() throws Exception {
        mockMvc.perform(get("/api/events/{id}/timetable", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manual").value(false))
                .andExpect(jsonPath("$.sets.length()").value(2))
                .andExpect(jsonPath("$.utcOffsetMinutes").value(120))
                .andExpect(jsonPath("$.sets[0].stage").value("MAIN FLOOR"))
                .andExpect(jsonPath("$.sets[0].startsAt").value(day + "T22:00:00"))
                .andExpect(jsonPath("$.sets[0].artist").value("Derango"))
                .andExpect(jsonPath("$.sets[0].artistSlug").value("derango"))
                .andExpect(jsonPath("$.sets[1].artist").value("Megalopsy"));

        // Artists behind set times are in the artist index too
        mockMvc.perform(get("/api/artists/{slug}", "megalopsy"))
                .andExpect(jsonPath("$.upcomingEvents[0].id").value(eventId));
    }

    @Test
    void anAdminsTimetableStaysUntilHandedBack() throws Exception {
        setTimetable("""
                [{"stage": "Main", "artist": "Ajja", "startsAt": "%sT23:00:00", "endsAt": "%sT01:00:00"}]
                """.formatted(day, nextDay))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manual").value(true))
                .andExpect(jsonPath("$.sets.length()").value(1));

        lineupScanRepository.save(new LineupScan(eventId, Instant.EPOCH));
        artistIndexer.indexNextBatch();
        mockMvc.perform(get("/api/events/{id}/timetable", eventId))
                .andExpect(jsonPath("$.sets[0].artist").value("Ajja"));

        mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].kind").value("EVENT_TIMETABLE"))
                .andExpect(jsonPath("$[0].before").value("2 sets, from the line-up"))
                .andExpect(jsonPath("$[0].after").value("1 sets, set by hand"));

        mockMvc.perform(delete("/api/admin/events/{id}/timetable", eventId).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.manual").value(false))
                .andExpect(jsonPath("$.sets.length()").value(0));
        artistIndexer.indexNextBatch();
        mockMvc.perform(get("/api/events/{id}/timetable", eventId))
                .andExpect(jsonPath("$.sets.length()").value(2));
    }

    @Test
    void setsNeedAnArtistAndAStartBeforeTheEnd() throws Exception {
        setTimetable("[{\"artist\": \"\", \"startsAt\": \"%sT23:00:00\"}]".formatted(day))
                .andExpect(status().isBadRequest());
        setTimetable("[{\"artist\": \"Ajja\", \"startsAt\": \"%sT23:00:00\", \"endsAt\": \"%sT22:00:00\"}]"
                .formatted(day, day))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ajja ends before they start"));
    }

    private ResultActions setTimetable(String sets) throws Exception {
        return mockMvc.perform(put("/api/admin/events/{id}/timetable", eventId).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"sets\": %s}".formatted(sets)));
    }
}
