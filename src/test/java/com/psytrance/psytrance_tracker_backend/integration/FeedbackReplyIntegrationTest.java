package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins answer feedback by email; the answer is kept with the message */
class FeedbackReplyIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Test
    void anAnswerGoesToTheAddressInTheMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        String adminToken = login(ADMIN, PASSWORD);
        String marker = uniqueUsername();
        mockMvc.perform(post("/api/feedback").contentType(MediaType.APPLICATION_JSON).content("""
                {"kind": "IDEA", "message": "With an address %s", "email": "fan@example.com"}
                """.formatted(marker)));
        mockMvc.perform(post("/api/feedback").contentType(MediaType.APPLICATION_JSON).content("""
                {"kind": "IDEA", "message": "Without an address %s"}
                """.formatted(marker)));

        String list = mockMvc.perform(get("/api/admin/feedback").header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> withAddress = JsonPath.read(list, "$[?(@.message == 'With an address %s')].id".formatted(marker));
        List<Integer> without = JsonPath.read(list, "$[?(@.message == 'Without an address %s')].id".formatted(marker));

        mockMvc.perform(post("/api/admin/feedback/{id}/reply", withAddress.get(0)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Thanks, it is on the list!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyTo").value("fan@example.com"))
                .andExpect(jsonPath("$.replyText").value("Thanks, it is on the list!"))
                .andExpect(jsonPath("$.repliedBy").value(ADMIN));
        mockMvc.perform(post("/api/admin/feedback/{id}/reply", without.get(0)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Hello?\"}"))
                .andExpect(status().isConflict());
    }
}
