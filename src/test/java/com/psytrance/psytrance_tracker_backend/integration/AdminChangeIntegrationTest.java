package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin history lists every change, and a change can be undone once, unless a newer one came after it. */
class AdminChangeIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin";
    private static final String PASSWORD = "secret-password-123";

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
    void anUndoPutsTheOldGenresBackAndIsLoggedToo() throws Exception {
        setGenres("Undo Test Act", "[\"forest\"]").andExpect(status().isOk());
        setGenres("Undo Test Act", "[\"forest\", \"twilight\"]").andExpect(status().isOk());
        long newest = newestChangeId();

        mockMvc.perform(post("/api/admin/changes/{id}/undo", newest).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newest))
                .andExpect(jsonPath("$.undoneBy").isNumber());

        mockMvc.perform(get("/api/admin/artists").param("search", "undo test act").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].genres.length()").value(1))
                .andExpect(jsonPath("$[0].genres[0]").value("forest"));

        mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].undoOf").value(newest))
                .andExpect(jsonPath("$[0].before").value("forest, twilight"))
                .andExpect(jsonPath("$[0].after").value("forest"))
                .andExpect(jsonPath("$[0].username").value(ADMIN));

        // Only once
        mockMvc.perform(post("/api/admin/changes/{id}/undo", newest).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        setGenres("Undo Test Act", "[]").andExpect(status().isOk());
    }

    @Test
    void anOlderChangeCantBeUndoneOverANewerOne() throws Exception {
        setGenres("Undo Order Act", "[\"goa\"]").andExpect(status().isOk());
        long older = newestChangeId();
        setGenres("Undo Order Act", "[\"psy\"]").andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/changes/{id}/undo", older).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Undo Order Act was changed again since. Undo the newer change first."));

        setGenres("Undo Order Act", "[]").andExpect(status().isOk());
    }

    @Test
    void onlyAdminsSeeTheHistory() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(login(username, PASSWORD))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/changes/{id}/undo", 1).header("Authorization", bearer(login(username, PASSWORD))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/changes"))
                .andExpect(status().isUnauthorized());
    }

    private long newestChangeId() throws Exception {
        String body = mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).isNotEmpty();
        return ids.get(0).longValue();
    }

    private ResultActions setGenres(String name, String genres) throws Exception {
        return mockMvc.perform(put("/api/admin/artists/genres").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"genres\": %s}".formatted(name, genres)));
    }
}
