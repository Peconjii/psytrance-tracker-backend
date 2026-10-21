package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins hide line-up words that aren't artists and merge two spellings of one artist. */
class ArtistCleanupIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin";
    private static final String PASSWORD = "secret-password-123";
    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(600_000);

    @Autowired
    private ArtistIndexer artistIndexer;

    @Autowired
    private LineupScanRepository lineupScanRepository;

    private String adminToken;
    private long eventId;
    // Unique per test: all tests share one database
    private String suffix;

    @BeforeEach
    void eventWithNoise() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);

        eventId = NEXT_EVENT_ID.getAndIncrement();
        suffix = Long.toString(eventId, 36);
        String lineUp = "Zorgon Bar %s\r\nDJ Tester %s\r\nTester %s".formatted(suffix, suffix, suffix);
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(eventId, "Cleanup Party",
                LocalDate.now().plusDays(3) + "T22:00:00+02:00", null, "Club", "Scheduled", "Serbia", "Beograd",
                44.8, 20.4, null, null)));
        when(goabaseClient.fetchParty(eventId)).thenReturn(Optional.of(new GoabasePartyDetails(eventId, "Cleanup Party",
                LocalDate.now().plusDays(3) + "T22:00:00+02:00", null, "Club", "Scheduled", "Serbia", "Beograd",
                44.8, 20.4, null, null, null, lineUp, "", "", "", null, null)));
        artistIndexer.indexNextBatch();
    }

    @Test
    void aHiddenWordLeavesTheLineUpsAndStaysOutUntilShownAgain() throws Exception {
        String slug = "zorgon-bar-" + suffix;
        mockMvc.perform(get("/api/artists/{slug}", slug)).andExpect(status().isOk());

        hide("Zorgon Bar " + suffix, true).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/artists/{slug}", slug)).andExpect(status().isNotFound());

        readAgain();
        mockMvc.perform(get("/api/artists/{slug}", slug)).andExpect(status().isNotFound());

        // Undo in the history shows it again; the next read of the line-up brings it back
        String history = mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].kind").value("ARTIST_HIDDEN"))
                .andExpect(jsonPath("$[0].after").value("not an artist"))
                .andReturn().getResponse().getContentAsString();
        long changeId = ((Number) JsonPath.read(history, "$[0].id")).longValue();
        mockMvc.perform(post("/api/admin/changes/{id}/undo", changeId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        readAgain();
        mockMvc.perform(get("/api/artists/{slug}", slug)).andExpect(status().isOk());
    }

    @Test
    void twoSpellingsBecomeOneArtistAlsoInLaterLineUps() throws Exception {
        merge("DJ Tester " + suffix, "Tester " + suffix).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/artists/{slug}", "dj-tester-" + suffix)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/artists/{slug}", "tester-" + suffix))
                .andExpect(jsonPath("$.upcomingEvents.length()").value(1));

        readAgain();
        mockMvc.perform(get("/api/artists/{slug}", "dj-tester-" + suffix)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/artists/{slug}", "tester-" + suffix))
                .andExpect(jsonPath("$.upcomingEvents.length()").value(1));

        mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].kind").value("ARTIST_MERGED"))
                .andExpect(jsonPath("$[0].after").value("Tester " + suffix));
    }

    @Test
    void mapArtistsCantBeHiddenOrMergedAway() throws Exception {
        hide("Derango", true).andExpect(status().isConflict());
        merge("Tester " + suffix, "Tester " + suffix).andExpect(status().isBadRequest());
        merge("Nobody Called This " + suffix, "Tester " + suffix).andExpect(status().isNotFound());
    }

    private void readAgain() {
        lineupScanRepository.save(new LineupScan(eventId, Instant.EPOCH));
        artistIndexer.indexNextBatch();
    }

    private ResultActions hide(String name, boolean hidden) throws Exception {
        return mockMvc.perform(post("/api/admin/artists/hidden").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"%s\", \"hidden\": %s}".formatted(name, hidden)));
    }

    private ResultActions merge(String from, String into) throws Exception {
        return mockMvc.perform(post("/api/admin/artists/merge").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"from\": \"%s\", \"into\": \"%s\"}".formatted(from, into)));
    }
}
