package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FavoriteIntegrationTest extends IntegrationTest {

    private static final String BOOM_FESTIVAL = """
            {"eventId": "115531", "eventName": "Boom Festival"}
            """;

    @Test
    void userCanAddListAndRemoveTheirFavorites() throws Exception {
        String username = uniqueUsername();
        long userId = register(username, "password123");
        String token = login(username, "password123");

        mockMvc.perform(post("/api/favorites/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOM_FESTIVAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("115531"));

        mockMvc.perform(get("/api/favorites/{userId}", userId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventName").value("Boom Festival"));

        mockMvc.perform(delete("/api/favorites/{userId}", userId)
                        .param("eventId", "115531")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/favorites/{userId}", userId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void addingTheSameEventTwiceIsAConflict() throws Exception {
        String username = uniqueUsername();
        long userId = register(username, "password123");
        String token = login(username, "password123");

        mockMvc.perform(post("/api/favorites/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOM_FESTIVAL))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/favorites/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOM_FESTIVAL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Event is already in your favorites"));
    }

    @Test
    void usersCannotReadOrChangeSomeoneElsesFavorites() throws Exception {
        String alice = uniqueUsername();
        long aliceId = register(alice, "password123");

        String mallory = uniqueUsername();
        register(mallory, "password123");
        String malloryToken = login(mallory, "password123");

        mockMvc.perform(get("/api/favorites/{userId}", aliceId).header(HttpHeaders.AUTHORIZATION, bearer(malloryToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/favorites/{userId}", aliceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(malloryToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOM_FESTIVAL))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/favorites/{userId}", aliceId)
                        .param("eventId", "115531")
                        .header(HttpHeaders.AUTHORIZATION, bearer(malloryToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void favoritesNeedLogin() throws Exception {
        mockMvc.perform(get("/api/favorites/{userId}", 1))
                .andExpect(status().isUnauthorized());
    }
}
