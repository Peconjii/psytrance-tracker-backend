package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Artists on the galaxy have a page even before any line-up names them, so they can claim and edit it */
class GalaxyArtistPagesIntegrationTest extends IntegrationTest {

    @Test
    void anArtistOnlyOnTheGalaxyHasAPage() throws Exception {
        // Atriohm orbits a star in genres.json and plays at no event in the tests
        mockMvc.perform(get("/api/artists/atriohm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Atriohm"))
                .andExpect(jsonPath("$.upcomingEvents.length()").value(0));
    }
}
