package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One search box for the whole site, open to guests */
class SearchIntegrationTest extends IntegrationTest {

    @Autowired
    private ArtistService artistService;

    @Test
    void findsArtistsEventsByNameOrTownLabelsAndFestivals() throws Exception {
        String unique = uniqueLetters();
        long event = 997_100L + (long) (Math.random() * 800);
        artistService.saveLineUp(event, List.of("Searchable " + unique), Instant.now());
        artistService.saveSummary(new EventDto(event, "Gathering " + unique, "Town" + unique, "Serbia", "Festival", "Scheduled",
                LocalDate.now().plusDays(10), null, null, null, 1.0, 1.0));

        mockMvc.perform(get("/api/search").param("q", "searchable " + unique))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artists[0].name").value("Searchable " + unique))
                .andExpect(jsonPath("$.artists[0].upcomingEvents").value(1));
        mockMvc.perform(get("/api/search").param("q", "town" + unique))
                .andExpect(jsonPath("$.events[0].id").value((int) event));
        mockMvc.perform(get("/api/search").param("q", "iboga"))
                .andExpect(jsonPath("$.labels").value(hasItem("Iboga Records")));
        mockMvc.perform(get("/api/search").param("q", "ozora"))
                .andExpect(jsonPath("$.festivals[0].id").value("ozora"));
        // Wildcards are letters like any other
        mockMvc.perform(get("/api/search").param("q", "%%"))
                .andExpect(jsonPath("$.artists.length()").value(0));
        mockMvc.perform(get("/api/search").param("q", "a")).andExpect(status().isBadRequest());
    }
}
