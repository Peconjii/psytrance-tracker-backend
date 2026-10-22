package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The numbers on the artist and label pages */
class StatsIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(994_000);
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistService artistService;

    @Test
    void anArtistsGigsAreCountedByCountryAndYearAndTheirFollowersByMonth() throws Exception {
        String unique = uniqueLetters();
        String name = "Stats Act " + unique;
        String slug = "stats-act-" + unique;
        long past = NEXT_EVENT_ID.getAndIncrement();
        long next = NEXT_EVENT_ID.getAndIncrement();
        artistService.saveLineUp(past, List.of(name), Instant.now());
        artistService.saveSummary(event(past, LocalDate.now().minusDays(40), "Portugal"));
        artistService.saveLineUp(next, List.of(name), Instant.now());
        artistService.saveSummary(event(next, LocalDate.now().plusDays(10), "Germany"));

        String username = uniqueUsername();
        register(username, PASSWORD);
        mockMvc.perform(put("/api/me/follows/{slug}", slug).header("Authorization", bearer(login(username, PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/artists/{slug}/stats", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gigs.total").value(2))
                .andExpect(jsonPath("$.gigs.upcoming").value(1))
                .andExpect(jsonPath("$.gigs.past").value(1))
                .andExpect(jsonPath("$.gigs.byCountry[*].key").value(hasItem("Portugal")))
                .andExpect(jsonPath("$.followers").value(1))
                .andExpect(jsonPath("$.followersByMonth[0].total").value(1));
        mockMvc.perform(get("/api/artists/{slug}/stats", "nobody-" + unique)).andExpect(status().isNotFound());
    }

    @Test
    void aLabelCountsItsCrewTogether() throws Exception {
        long next = NEXT_EVENT_ID.getAndIncrement();
        artistService.saveLineUp(next, List.of("Shakta"), Instant.now()); // on Dragonfly Records in labels.json
        artistService.saveSummary(event(next, LocalDate.now().plusDays(15), "Hungary"));

        mockMvc.perform(get("/api/labels/stats").param("label", "Dragonfly Records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crew").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.gigs.upcoming").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.upcoming[*].eventId").value(hasItem((int) next)));
        mockMvc.perform(get("/api/labels/stats").param("label", "No Such Records")).andExpect(status().isNotFound());
    }

    private static EventDto event(long id, LocalDate day, String country) {
        return new EventDto(id, "Stats Night " + id, "Town", country, "Club", "Scheduled", day, null, null, null, 1.0, 1.0);
    }
}
