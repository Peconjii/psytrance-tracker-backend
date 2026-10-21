package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins move artists between map genres; the map reads the placements from the API. */
class AdminArtistIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin";
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private AdminChangeRepository changeRepository;

    private String adminToken;

    @BeforeEach
    void admin() throws Exception {
        // The admin may already exist: all test classes share one database
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
    }

    @Test
    void placementsStartFromTheMapFile() throws Exception {
        mockMvc.perform(get("/api/map/placements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forest").isArray())
                .andExpect(jsonPath("$.goa[0]").exists());
    }

    @Test
    void anAdminMovesAnArtistAndTheChangeIsLogged() throws Exception {
        long changesBefore = changeRepository.count();

        setGenres("Hux Flux", "[\"twilight\", \"dark\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hux Flux"))
                .andExpect(jsonPath("$.genres.length()").value(2))
                .andExpect(jsonPath("$.onMap").value(true));

        String placements = mockMvc.perform(get("/api/map/placements")).andReturn().getResponse().getContentAsString();
        List<String> dark = JsonPath.read(placements, "$.dark");
        assertThat(dark).contains("Hux Flux");

        mockMvc.perform(get("/api/admin/artists").param("search", "hux flux").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].genres[1]").value("dark"));

        assertThat(changeRepository.count()).isEqualTo(changesBefore + 1);
        AdminChange change = changeRepository.findAll().stream().reduce((a, b) -> b).orElseThrow();
        assertThat(change.getUsername()).isEqualTo(ADMIN);
        assertThat(change.getAfterValue()).isEqualTo("twilight, dark");

        // Back as it was, for the other tests
        setGenres("Hux Flux", "[\"twilight\"]").andExpect(status().isOk());
    }

    @Test
    void anArtistFromTheLineUpsCanBePutOnTheMapAndTakenOff() throws Exception {
        setGenres("Brand New Act", "[\"forest\"]")
                .andExpect(jsonPath("$.onMap").value(true));
        setGenres("brand new act", "[]")
                .andExpect(jsonPath("$.onMap").value(false));

        String placements = mockMvc.perform(get("/api/map/placements")).andReturn().getResponse().getContentAsString();
        assertThat(placements).doesNotContain("Brand New Act");
    }

    @Test
    void unknownGenresAndNonAdminsAreRefused() throws Exception {
        setGenres("Hux Flux", "[\"polka\"]").andExpect(status().isBadRequest());

        String username = uniqueUsername();
        register(username, PASSWORD);
        mockMvc.perform(put("/api/admin/artists/genres").header("Authorization", bearer(login(username, PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Hux Flux\", \"genres\": []}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/artists"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions setGenres(String name, String genres) throws Exception {
        return mockMvc.perform(put("/api/admin/artists/genres").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"genres\": %s}".formatted(name, genres)));
    }
}
