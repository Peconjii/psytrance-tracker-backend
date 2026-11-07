package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.service.artists.ArtistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Albums and festival sets live in the database: the bundled files fill it once, artists and festivals edit their own */
class DiscographyIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(975_000);
    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistService artistService;

    private String adminToken;

    @BeforeEach
    void anAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
    }

    @Test
    void theBundledAlbumsAndSetsAreInTheDatabase() throws Exception {
        mockMvc.perform(get("/api/map/discography"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['astral-projection'].country").value("Israel"))
                .andExpect(jsonPath("$['astral-projection'].albums[*].title").value(hasItem("Trust in Trance")));
        mockMvc.perform(get("/api/map/sets"))
                .andExpect(jsonPath("$[?(@.artistSlug == 'astral-projection')].festivalId").value(hasItem("ozora")));
        mockMvc.perform(get("/api/artists/astral-projection/discography"))
                .andExpect(jsonPath("$.since").value(1993));
    }

    @Test
    void anArtistsAlbumsAreWrittenByThemOrAnAdminAndAFestivalWritesItsSets() throws Exception {
        long gig = NEXT_EVENT_ID.getAndIncrement();
        String unique = uniqueLetters();
        String name = "Album Test " + unique;
        String slug = "album-test-" + unique;
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(gig, "Album Fest",
                LocalDate.now().plusDays(7) + "T22:00:00+02:00", null, "Festival", "Scheduled", "Serbia", "Novi Sad",
                45.2, 19.8, null, null)));
        artistService.saveLineUp(gig, List.of(name), Instant.now());

        String albums = """
                {"country": "Serbia", "since": 2015, "albums": [{"title": "First Light", "year": 2018}, {"title": "Deep Roots"}]}
                """;
        mockMvc.perform(put("/api/artists/{slug}/discography", slug).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(albums))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albums[0].title").value("First Light"))
                .andExpect(jsonPath("$.albums[1].year").doesNotExist());
        mockMvc.perform(get("/api/map/discography")).andExpect(jsonPath("$['" + slug + "'].country").value("Serbia"));

        // Someone who isn't the artist, and a guest
        String user = uniqueUsername();
        register(user, PASSWORD);
        String userToken = login(user, PASSWORD);
        mockMvc.perform(put("/api/artists/{slug}/discography", slug).header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(albums))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/artists/{slug}/discography", slug).contentType(MediaType.APPLICATION_JSON).content(albums))
                .andExpect(status().isUnauthorized());

        // The festival's sets: written for the festival, they land in the artist's "Festivals played"
        String sets = "[{\"artist\": \"%s\", \"year\": 2024, \"url\": \"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}]".formatted(name);
        mockMvc.perform(put("/api/festivals/sets").param("id", "naturaiz").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(sets))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].artist").value(name));
        mockMvc.perform(get("/api/map/sets"))
                .andExpect(jsonPath("$[?(@.artistSlug == '" + slug + "')].festivalId").value(hasItem("naturaiz")));
        mockMvc.perform(get("/api/artists/{slug}", slug))
                .andExpect(jsonPath("$.festivals[*].year").value(hasItem(2024)));
        mockMvc.perform(put("/api/festivals/sets").param("id", "naturaiz").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON).content(sets))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/festivals/sets").param("id", "naturaiz").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(sets.replace("https://", "javascript:")))
                .andExpect(status().isBadRequest());
    }
}
