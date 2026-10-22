package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Deleting reviews: the author their own, admins anyone's (and that is in the history) */
class ReviewModerationIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Test
    void theAuthorAndAdminsDeleteReviewsOthersCannot() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        String adminToken = login(ADMIN, PASSWORD);
        String author = uniqueUsername();
        register(author, PASSWORD);
        String authorToken = login(author, PASSWORD);
        String stranger = uniqueUsername();
        register(stranger, PASSWORD);
        String strangerToken = login(stranger, PASSWORD);

        long spam = review(authorToken, "Buy cheap tickets at spam.example");
        long own = review(authorToken, "Great night");

        mockMvc.perform(get("/api/admin/reviews").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[*].id").value(hasItem((int) spam)));
        mockMvc.perform(delete("/api/reviews/{id}", spam).header("Authorization", bearer(strangerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/reviews/{id}", spam).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].kind").value("REVIEW_DELETED"))
                .andExpect(jsonPath("$[0].before").value("5 ★ Buy cheap tickets at spam.example"));

        mockMvc.perform(delete("/api/reviews/{id}", own).header("Authorization", bearer(authorToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reviews/user").header("Authorization", bearer(authorToken)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // A review of its own event, so the author can write several
    private long review(String token, String comment) throws Exception {
        String body = mockMvc.perform(post("/api/reviews").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"eventId": "%s", "rating": 5, "comment": "%s"}
                                """.formatted("moderation-" + uniqueUsername(), comment)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
