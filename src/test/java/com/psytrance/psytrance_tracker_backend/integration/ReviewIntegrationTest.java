package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends IntegrationTest {

    @Test
    void anyoneCanReadReviewsButOnlyLoggedInUsersCanWriteThem() throws Exception {
        String eventId = "review-test-" + uniqueUsername();
        String review = """
                {"eventId": "%s", "rating": 5, "comment": "Amazing sunrise set"}
                """.formatted(eventId);

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isUnauthorized());

        String username = uniqueUsername();
        register(username, "password123");
        String token = login(username, "password123");

        mockMvc.perform(post("/api/reviews")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(review))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        // No token needed to read
        mockMvc.perform(get("/api/reviews/event/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].comment").value("Amazing sunrise set"));
    }

    @Test
    void reviewingTheSameEventAgainUpdatesTheReviewInsteadOfAddingOne() throws Exception {
        String eventId = "review-test-" + uniqueUsername();
        String username = uniqueUsername();
        register(username, "password123");
        String token = login(username, "password123");

        for (int rating : new int[]{2, 4}) {
            mockMvc.perform(post("/api/reviews")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"eventId": "%s", "rating": %d}
                                    """.formatted(eventId, rating)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/reviews/event/{eventId}", eventId))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rating").value(4));

        mockMvc.perform(get("/api/reviews/user").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void ratingMustBeBetweenOneAndFive() throws Exception {
        String username = uniqueUsername();
        register(username, "password123");
        String token = login(username, "password123");

        mockMvc.perform(post("/api/reviews")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": "115531", "rating": 6}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
    }
}
