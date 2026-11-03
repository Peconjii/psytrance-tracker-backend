package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin -> Galaxy: a genre's texts and key tracks start from the map file and are fixed without a deploy. */
class GalaxyContentIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin";
    private static final String PASSWORD = "secret-password-123";
    // A genre no other test reads the content of; every test here puts it back as it found it
    private static final String GENRE = "psybreaks";

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

    private String content() throws Exception {
        return mockMvc.perform(get("/api/admin/map/content/{id}", GENRE).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String save(String body) throws Exception {
        return mockMvc.perform(put("/api/admin/map/content/{id}", GENRE).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theTextsAndTracksStartFromTheMapFileAndNothingIsVerified() throws Exception {
        mockMvc.perform(get("/api/map/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goa.description").value(org.hamcrest.Matchers.containsString("Goa")))
                .andExpect(jsonPath("$.goa.tracks[0].title").value("LSD"))
                .andExpect(jsonPath("$.goa.tracks[0].youtube").value("iIsVzSMRw8E"))
                .andExpect(jsonPath("$.goa.bpmMin").value(135))
                .andExpect(jsonPath("$.goa.verified").value(false))
                .andExpect(jsonPath("$.goa.tracks[0].verified").value(false));
    }

    @Test
    void anEditorFixesAGenreTheMapShowsItAtOnceAndTheChangeCanBeUndone() throws Exception {
        String before = content();

        save("""
                {"description": "Breakbeats under psychedelic leads.", "bass": "Rolling", "kick": null, "atmosphere": "",
                 "year": 2001, "bpmMin": 130, "bpmMax": 140, "verified": true,
                 "tracks": [{"artist": "Test Act", "title": "Test Track", "year": 2005,
                             "youtube": "https://www.youtube.com/watch?v=iIsVzSMRw8E&t=30s", "famous": false,
                             "verified": true, "drop": 212}]}
                """);

        mockMvc.perform(get("/api/map/content"))
                .andExpect(jsonPath("$.%s.description".formatted(GENRE)).value("Breakbeats under psychedelic leads."))
                .andExpect(jsonPath("$.%s.atmosphere".formatted(GENRE)).doesNotExist())
                .andExpect(jsonPath("$.%s.verified".formatted(GENRE)).value(true))
                .andExpect(jsonPath("$.%s.tracks.length()".formatted(GENRE)).value(1))
                // The whole link becomes the video id
                .andExpect(jsonPath("$.%s.tracks[0].youtube".formatted(GENRE)).value("iIsVzSMRw8E"))
                .andExpect(jsonPath("$.%s.tracks[0].drop".formatted(GENRE)).value(212));

        AdminChange change = changeRepository.findTop200ByOrderByCreatedAtDescIdDesc().stream()
                .filter(c -> AdminChange.GENRE_CONTENT.equals(c.getKind()) && GENRE.equals(c.getTarget()))
                .findFirst().orElseThrow();
        assertThat(change.getUsername()).isEqualTo(ADMIN);

        mockMvc.perform(post("/api/admin/changes/{id}/undo", change.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(JsonPath.<Map<String, Object>>read(content(), "$")).isEqualTo(JsonPath.read(before, "$"));

        // Once only
        mockMvc.perform(post("/api/admin/changes/{id}/undo", change.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void anUndoNeverThrowsAwayANewerFix() throws Exception {
        String before = content();
        String first = before.replaceFirst("\"description\":\"[^\"]*\"", "\"description\":\"First fix\"");
        save(first);
        long firstChange = newestChangeId();
        save(before.replaceFirst("\"description\":\"[^\"]*\"", "\"description\":\"Second fix\""));

        mockMvc.perform(post("/api/admin/changes/{id}/undo", firstChange).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        save(before);
    }

    @Test
    void aTrackWithoutAVideoCanBeSaved() throws Exception {
        // Some tracks in the map file have no video (the map links a YouTube search); their genre must still save
        String before = content();
        save("""
                {"description": "x", "tracks": [{"artist": "Age of Love", "title": "The Age of Love", "youtube": null},
                                                {"artist": "A", "title": "B", "youtube": "", "drop": 100}]}
                """);
        mockMvc.perform(get("/api/map/content"))
                .andExpect(jsonPath("$.%s.tracks[0].youtube".formatted(GENRE)).doesNotExist())
                .andExpect(jsonPath("$.%s.tracks[1].drop".formatted(GENRE)).value(100));
        save(before);
    }

    @Test
    void wrongTracksAndGenresAreRefused() throws Exception {
        mockMvc.perform(put("/api/admin/map/content/{id}", GENRE).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "x", "tracks": [{"artist": "A", "title": "", "youtube": "iIsVzSMRw8E"}]}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/map/content/{id}", GENRE).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "x", "tracks": [{"artist": "A", "title": "B", "youtube": "not a video"}]}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/map/content/{id}", "no-such-genre").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"x\", \"tracks\": []}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsAndEditorsMayChangeIt() throws Exception {
        String username = uniqueUsername();
        register(username, "password123");
        String token = login(username, "password123");

        mockMvc.perform(put("/api/admin/map/content/{id}", GENRE).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\": \"x\", \"tracks\": []}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/map/content/{id}", GENRE))
                .andExpect(status().isUnauthorized());
        assertThat(JsonPath.<List<Object>>read(content(), "$.tracks")).isNotNull();
    }

    private long newestChangeId() {
        return changeRepository.findTop200ByOrderByCreatedAtDescIdDesc().get(0).getId();
    }
}
