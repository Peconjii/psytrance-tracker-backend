package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.service.ArtistIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Events on the galaxy map: line-ups and keywords read from (fake) Goabase, turned into Map of Psy genres. */
class MapIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(800_000);

    @Autowired
    private ArtistIndexer artistIndexer;

    private long forestParty;
    private long keywordParty;
    private long unknownParty;
    private long nextMonth;

    @BeforeEach
    void eventsOnGoabase() {
        forestParty = NEXT_EVENT_ID.getAndIncrement();
        keywordParty = NEXT_EVENT_ID.getAndIncrement();
        unknownParty = NEXT_EVENT_ID.getAndIncrement();
        nextMonth = NEXT_EVENT_ID.getAndIncrement();
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(
                party(forestParty, "Forest Gathering", 1),
                party(keywordParty, "Twilight Night", 2),
                party(unknownParty, "Garden Party", 3),
                party(nextMonth, "Next Month Festival", 20)));
        when(goabaseClient.fetchParty(forestParty)).thenReturn(Optional.of(
                lineUp(forestParty, "DARKPSY / FOREST STAGE\r\nDerango\r\nMegalopsy\r\nLocal Hero")));
        when(goabaseClient.fetchParty(keywordParty)).thenReturn(Optional.of(lineUp(keywordParty, "Local Hero")));
        when(goabaseClient.fetchKeywords(keywordParty)).thenReturn(List.of("Psytrance", "Twilight"));
        when(goabaseClient.fetchParty(unknownParty)).thenReturn(Optional.of(lineUp(unknownParty, "Local Hero")));
        when(goabaseClient.fetchParty(nextMonth)).thenReturn(Optional.of(lineUp(nextMonth, "Vini Vici")));
    }

    @Test
    void eventsOfTheWeekCarryTheirGenres() throws Exception {
        artistIndexer.indexNextBatch();

        mockMvc.perform(get("/api/map/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nameParty").value("Forest Gathering"))
                .andExpect(jsonPath("$[0].lineUpRead").value(true))
                // Derango and Megalopsy are forest on the map; Local Hero only has the stage header
                .andExpect(jsonPath("$[0].genres[0].genreId").value("forest"))
                .andExpect(jsonPath("$[0].genres[0].source").value("ARTIST"))
                .andExpect(jsonPath("$[0].genres[1].genreId").value("dark"))
                .andExpect(jsonPath("$[0].genres[1].source").value("STAGE"))
                .andExpect(jsonPath("$[1].genres[0].genreId").value("twilight"))
                .andExpect(jsonPath("$[1].genres[0].source").value("KEYWORD"))
                .andExpect(jsonPath("$[1].genres[0].share").value(1.0))
                // Nothing points to a genre: the harbour for unknown genres
                .andExpect(jsonPath("$[2].genres.length()").value(0));
    }

    @Test
    void monthAlsoShowsLaterEventsAndUnreadLineUpsAreMarked() throws Exception {
        mockMvc.perform(get("/api/map/events").param("period", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].nameParty").value("Next Month Festival"))
                .andExpect(jsonPath("$[3].lineUpRead").value(false));
    }

    @Test
    void genresOfAPageOfEventsComeInOneRequest() throws Exception {
        artistIndexer.indexNextBatch();

        mockMvc.perform(get("/api/map/genres").param("ids", forestParty + "," + unknownParty))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + forestParty + "'][0].genreId").value("forest"))
                // No known genre: not in the answer at all
                .andExpect(jsonPath("$['" + unknownParty + "']").doesNotExist());
    }

    @Test
    void tooManyEventsAtOnceIsABadRequest() throws Exception {
        String ids = String.join(",", LongStream.rangeClosed(1, 101).mapToObj(String::valueOf).toList());
        mockMvc.perform(get("/api/map/genres").param("ids", ids))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPeriodIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/map/events").param("period", "YEAR"))
                .andExpect(status().isBadRequest());
    }

    private static GoabaseParty party(long id, String name, int daysFromNow) {
        return new GoabaseParty(id, name, LocalDate.now().plusDays(daysFromNow) + "T22:00:00+02:00", null,
                "Open Air", "Scheduled", "Serbia", "Novi Sad", 45.25, 19.84, null, null);
    }

    private static GoabasePartyDetails lineUp(long id, String lineUp) {
        return new GoabasePartyDetails(id, "Event " + id, LocalDate.now().plusDays(1) + "T22:00:00+02:00", null,
                "Open Air", "Scheduled", "Serbia", "Novi Sad", 45.25, 19.84, null, null, null, lineUp,
                "", "", "", null, null);
    }
}
