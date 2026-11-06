package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/** A festival's and a label's map details, changed from the site without a deploy, and undone from History */
class StationDetailsIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin";
    private static final String PASSWORD = "secret-password-123";
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken == null) {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                    {"username": "%s", "email": "%s@example.com", "password": "%s"}
                    """.formatted(ADMIN, ADMIN, PASSWORD)));
            adminToken = login(ADMIN, PASSWORD);
        }
    }

    @Test
    void anAdminRenamesAFestivalAndChangesItsGenresThenUndoesIt() throws Exception {
        mockMvc.perform(put("/api/map/details/FESTIVAL").param("key", "psypek").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Psypek Gathering", "genres": ["forest", "goa"], "country": " "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Psypek Gathering"))
                .andExpect(jsonPath("$.country").doesNotExist());

        mockMvc.perform(get("/api/map/details"))
                .andExpect(jsonPath("$[?(@.key == 'psypek')].details.genres[0]").value("forest"));
        mockMvc.perform(get("/api/festivals/page").param("id", "psypek"))
                .andExpect(jsonPath("$.name").value("Psypek Gathering"));

        String history = mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(history, "$[?(@.target == 'FESTIVAL:psypek')].id");
        mockMvc.perform(post("/api/admin/changes/{id}/undo", ids.get(0)).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/festivals/page").param("id", "psypek"))
                .andExpect(jsonPath("$.name").value("Psypek"));
    }

    @Test
    void aLabelKeepsItsNameAndUnknownGenresAreRefused() throws Exception {
        mockMvc.perform(put("/api/map/details/LABEL").param("key", "Iboga Records").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Something Else", "since": 1997}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.since").value(1997));
        mockMvc.perform(put("/api/map/details/LABEL").param("key", "Iboga Records").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"genres": ["not-a-genre"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void someoneWhoIsNotTrustedCantChangeIt() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        String token = login(username, PASSWORD);
        mockMvc.perform(get("/api/map/details/FESTIVAL").param("key", "ozora").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.canEdit").value(false));
        mockMvc.perform(put("/api/map/details/FESTIVAL").param("key", "ozora").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Hacked\"}"))
                .andExpect(status().isForbidden());
        String all = mockMvc.perform(get("/api/map/details")).andReturn().getResponse().getContentAsString();
        assertThat(all).doesNotContain("Hacked");
    }
}
