package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.service.ArtistIndexer;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Line-ups read from (fake) Goabase by the indexer, then served as artist pages. */
class ArtistIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(900_000);

    @Autowired
    private ArtistIndexer artistIndexer;

    @Autowired
    private ArtistService artistService;

    private long soon;
    private long later;

    @BeforeEach
    void twoEventsOnGoabase() {
        hideEventsOtherTestsAdded();
        soon = NEXT_EVENT_ID.getAndIncrement();
        later = NEXT_EVENT_ID.getAndIncrement();
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(party(soon, "Soon Festival", 5), party(later, "Later Festival", 20)));
        when(goabaseClient.fetchParty(soon)).thenReturn(Optional.of(lineUp(soon, "LINE UP\r\nACE VENTURA (Iboga)\r\nAstrix live")));
        when(goabaseClient.fetchParty(later)).thenReturn(Optional.of(lineUp(later, "Ace Ventura\r\nËlement\r\n+ TBA")));
        assertThat(artistIndexer.indexNextBatch()).isEqualTo(2);
    }

    @Test
    void artistPageListsTheirUpcomingEventsSoonestFirst() throws Exception {
        mockMvc.perform(get("/api/artists/{slug}", "ace-ventura"))
                .andExpect(status().isOk())
                // Written in capitals at one event and normally at the other: the normal spelling wins
                .andExpect(jsonPath("$.name").value("Ace Ventura"))
                .andExpect(jsonPath("$.upcomingEvents.length()").value(2))
                .andExpect(jsonPath("$.upcomingEvents[0].nameParty").value("Soon Festival"))
                .andExpect(jsonPath("$.upcomingEvents[1].nameParty").value("Later Festival"));
    }

    @Test
    void listShowsTheBusiestArtistsFirstAndSearchIgnoresAccents() throws Exception {
        mockMvc.perform(get("/api/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].slug").value("ace-ventura"))
                .andExpect(jsonPath("$[0].upcomingEvents").value(2));

        mockMvc.perform(get("/api/artists").param("search", "element"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ëlement"));
    }

    @Test
    void readingALineUpAgainReplacesTheOldOne() throws Exception {
        // The organizer swapped Ace Ventura for someone else
        artistService.saveLineUp(later, List.of("Ëlement", "Vini Vici"), Instant.now());

        mockMvc.perform(get("/api/artists/{slug}", "ace-ventura"))
                .andExpect(jsonPath("$.upcomingEvents.length()").value(1));
        mockMvc.perform(get("/api/artists/{slug}", "vini-vici"))
                .andExpect(jsonPath("$.upcomingEvents[0].nameParty").value("Later Festival"));
    }

    @Test
    void unknownArtistIsNotFound() throws Exception {
        mockMvc.perform(get("/api/artists/{slug}", "nobody-plays-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Artist not found"));
    }

    @Test
    void eventPageLinksEachArtistToTheirPage() throws Exception {
        mockMvc.perform(get("/api/events/{id}/details", soon))
                .andExpect(jsonPath("$.lineUp[1].artist").value("ACE VENTURA"))
                .andExpect(jsonPath("$.lineUp[1].artistSlug").value("ace-ventura"))
                .andExpect(jsonPath("$.lineUp[0].artistSlug").doesNotExist());
    }

    private static GoabaseParty party(long id, String name, int daysFromNow) {
        return new GoabaseParty(id, name, LocalDate.now().plusDays(daysFromNow) + "T22:00:00+02:00", null,
                "Festival", "Scheduled", "Portugal", "Idanha-a-Nova", 39.9, -7.2, null, null);
    }

    private static GoabasePartyDetails lineUp(long id, String lineUp) {
        return new GoabasePartyDetails(id, "Festival " + id, LocalDate.now().plusDays(5) + "T22:00:00+02:00", null,
                "Festival", "Scheduled", "Portugal", "Idanha-a-Nova", 39.9, -7.2, null, null, null, lineUp,
                "", "", "", null, null);
    }
}
