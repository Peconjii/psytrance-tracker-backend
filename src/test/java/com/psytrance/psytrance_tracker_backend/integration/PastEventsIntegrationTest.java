package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The archive: the events the site kept that are over, newest first */
class PastEventsIntegrationTest extends IntegrationTest {

    @Autowired
    private ArtistService artistService;

    @Test
    void listsTheEventsThatAreOverNewestFirst() throws Exception {
        String unique = uniqueLetters();
        long older = 998_100L + (long) (Math.random() * 200);
        long newer = older + 300;
        long upcoming = older + 600;
        artistService.saveLineUp(newer, List.of("Archive Act " + unique), Instant.now());
        artistService.saveSummary(event(older, "Archive Older " + unique, LocalDate.now().minusDays(20)));
        artistService.saveSummary(event(newer, "Archive Newer " + unique, LocalDate.now().minusDays(5)));
        artistService.saveSummary(event(upcoming, "Archive Coming " + unique, LocalDate.now().plusDays(5)));

        mockMvc.perform(get("/api/events/past").param("search", "archive").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name =~ /.*%s/)].id".formatted(unique)).value(
                        org.hamcrest.Matchers.contains((int) newer, (int) older)))
                .andExpect(jsonPath("$.content[?(@.id == %d)].artists".formatted(newer)).value(org.hamcrest.Matchers.contains(1)));
    }

    private static EventDto event(long id, String name, LocalDate day) {
        return new EventDto(id, name, "Town", "Austria", "Club", "Scheduled", day, day, null, null, 1.0, 1.0);
    }
}
