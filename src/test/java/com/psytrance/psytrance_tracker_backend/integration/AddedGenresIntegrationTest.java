package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins add a genre (star) to the galaxy from the panel; it works like a genre of the map file */
class AddedGenresIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    private String adminToken;

    @BeforeEach
    void anAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
    }

    @Test
    void anAddedGenreGetsContentArtistsAndCanBeTakenOffAgain() throws Exception {
        String name = "Hypnotic " + uniqueLetters();
        String body = """
                {"name": "%s", "family": "dark", "year": 2016, "bpmMin": 140, "bpmMax": 148, "links": ["forest", "prog"],
                 "keywords": ["hypno"], "description": "Rolling, minimal and deep."}
                """.formatted(name);
        String id = com.psytrance.psytrance_tracker_backend.util.ArtistNames.slug(name);
        mockMvc.perform(post("/api/admin/map/genres").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.links[0]").value("forest"))
                .andExpect(jsonPath("$.x").isNumber());
        mockMvc.perform(get("/api/map/added-genres")).andExpect(jsonPath("$[*].id").value(hasItem(id)));
        // Its texts are in the galaxy content, and artists can orbit it
        mockMvc.perform(get("/api/map/content")).andExpect(jsonPath("$['" + id + "'].description").value("Rolling, minimal and deep."));
        mockMvc.perform(put("/api/admin/artists/genres").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Hypno Tester %s\", \"genres\": [\"%s\"]}".formatted(uniqueLetters(), id)))
                .andExpect(status().isOk());

        // Moved and renamed
        mockMvc.perform(put("/api/admin/map/genres/" + id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"keywords\"", "\"x\": 1234, \"y\": 987, \"keywords\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.x").value(1234));

        // The same name again, a link the map doesn't have, and anyone but an admin
        mockMvc.perform(post("/api/admin/map/genres").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace(name, name.toUpperCase())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/map/genres").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace(name, "Other " + name).replace("\"prog\"", "\"polka\"")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/map/genres").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace(name, "Goa trance")))
                .andExpect(status().isConflict());
        String user = uniqueUsername();
        register(user, PASSWORD);
        mockMvc.perform(post("/api/admin/map/genres").header("Authorization", bearer(login(user, PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace(name, "Mine " + name)))
                .andExpect(status().isForbidden());

        // Taken off: gone from the map and its content; a genre of the map file can't be taken off here
        mockMvc.perform(delete("/api/admin/map/genres/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/map/added-genres")).andExpect(jsonPath("$[*].id").value(not(hasItem(id))));
        mockMvc.perform(delete("/api/admin/map/genres/goa").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }
}
