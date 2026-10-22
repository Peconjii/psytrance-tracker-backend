package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistIndexer;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

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

/** Organizers come from who Goabase says runs each event, and from line-up names an admin moves out of the artists. */
class OrganizerIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(980_000);
    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistIndexer artistIndexer;

    @Autowired
    private ArtistService artistService;

    private String organizerName;
    private String organizerSlug;
    private long upcoming;

    @BeforeEach
    void anEventRunByAnOrganizer() {
        String unique = uniqueLetters();
        organizerName = "Crew " + unique;
        organizerSlug = "crew-" + unique;
        upcoming = NEXT_EVENT_ID.getAndIncrement();
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(upcoming, "Crew Night",
                LocalDate.now().plusDays(9) + "T22:00:00+02:00", null, "Club", "Scheduled", "Austria", "Vienna",
                48.2, 16.4, null, null)));
        when(goabaseClient.fetchParty(upcoming)).thenReturn(Optional.of(new GoabasePartyDetails(upcoming, "Crew Night",
                LocalDate.now().plusDays(9) + "T22:00:00+02:00", null, "Club", "Scheduled", "Austria", "Vienna", 48.2, 16.4,
                null, null, null, "Astrix live\r\nAce Ventura", "", "", "", organizerName, "https://crew.example.com")));
        artistIndexer.indexNextBatch();
    }

    @Test
    void theOrganizerPageListsTheirUpcomingAndPastEvents() throws Exception {
        long past = NEXT_EVENT_ID.getAndIncrement();
        artistService.saveSummary(EventDto.from(new GoabaseParty(past, "Crew Night Last Year",
                LocalDate.now().minusDays(300) + "T22:00:00+02:00", null, "Club", "Scheduled", "Austria", "Vienna",
                48.2, 16.4, null, null)), organizerSlug);

        mockMvc.perform(get("/api/organizers").param("search", organizerName))
                .andExpect(jsonPath("$[0].slug").value(organizerSlug))
                .andExpect(jsonPath("$[0].upcomingEvents").value(1));
        mockMvc.perform(get("/api/organizers/{slug}", organizerSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(organizerName))
                .andExpect(jsonPath("$.url").value("https://crew.example.com"))
                .andExpect(jsonPath("$.upcomingEvents[0].nameParty").value("Crew Night"))
                .andExpect(jsonPath("$.pastEvents[0].name").value("Crew Night Last Year"));
        mockMvc.perform(get("/api/organizers/{slug}", "nobody-runs-this"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdminMovesALineUpNameToTheOrganizers() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        String adminToken = login(ADMIN, PASSWORD);
        String name = "Presents " + uniqueLetters();
        artistService.saveLineUp(NEXT_EVENT_ID.getAndIncrement(), List.of(name), Instant.now());

        mockMvc.perform(post("/api/admin/artists/organizer").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isOk());
        String slug = "presents-" + name.substring("Presents ".length());
        mockMvc.perform(get("/api/artists/{slug}", slug)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/organizers/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
    }
}
