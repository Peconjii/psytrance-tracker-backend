package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The feedback form: anyone can send, only admins (app.admin-usernames) can read and handle it. */
class FeedbackIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin";
    private static final String PASSWORD = "secret-password-123";

    private String adminToken;
    // Every test sends from its own address, so the per-hour limit of one test doesn't affect another
    private String clientAddress;

    @BeforeEach
    void setUp() throws Exception {
        // All tests share one database: the admin account is created once
        if (adminToken == null) {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                    {"username": "%s", "email": "%s@example.com", "password": "%s"}
                    """.formatted(ADMIN, ADMIN, PASSWORD)));
            adminToken = login(ADMIN, PASSWORD);
        }
        clientAddress = "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    @Test
    void guestsCanSendFeedbackAndAdminsSeeItNewestFirst() throws Exception {
        String marker = UUID.randomUUID().toString();
        send("""
                {"kind": "CORRECTION", "aboutType": "ARTIST", "aboutRef": "Fungus Funk",
                 "message": "Fungus Funk is night full-on, not forest %s", "email": "fan@example.com"}
                """.formatted(marker))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/admin/feedback").param("status", "NEW")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> messages = JsonPath.read(body, "$[*].message");
        assertThat(messages).anyMatch(m -> m.contains(marker));
        assertThat(messages.get(0)).contains(marker);
        assertThat((String) JsonPath.read(body, "$[0].aboutRef")).isEqualTo("Fungus Funk");
        assertThat((Object) JsonPath.read(body, "$[0].username")).isNull();
    }

    @Test
    void loggedInSendersAreRememberedAndAdminsCanMarkFeedbackDone() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        String token = login(username, PASSWORD);
        String marker = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/feedback").header("Authorization", bearer(token)).header("X-Forwarded-For", clientAddress)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind": "IDEA", "message": "Show the timetable %s"}
                                """.formatted(marker)))
                .andExpect(status().isCreated());

        String list = mockMvc.perform(get("/api/admin/feedback").header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(list, "$[?(@.message =~ /.*" + marker + ".*/)].id");
        List<String> senders = JsonPath.read(list, "$[?(@.message =~ /.*" + marker + ".*/)].username");
        assertThat(senders).containsExactly(username);

        mockMvc.perform(patch("/api/admin/feedback/{id}", ids.get(0)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void onlyAdminsCanReadFeedback() throws Exception {
        mockMvc.perform(get("/api/admin/feedback"))
                .andExpect(status().isUnauthorized());

        String username = uniqueUsername();
        register(username, PASSWORD);
        mockMvc.perform(get("/api/admin/feedback").header("Authorization", bearer(login(username, PASSWORD))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void invalidFeedbackIsRejected() throws Exception {
        send("""
                {"kind": "IDEA", "message": "hi", "email": "not-an-email"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists())
                .andExpect(jsonPath("$.errors.email").exists());
        send("""
                {"kind": "WISHLIST", "message": "A kind that doesn't exist"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void botsFillingTheHiddenFieldAreQuietlyIgnored() throws Exception {
        String marker = UUID.randomUUID().toString();
        send("""
                {"kind": "OTHER", "message": "Cheap watches %s", "website": "http://spam.example"}
                """.formatted(marker))
                .andExpect(status().isCreated());

        String list = mockMvc.perform(get("/api/admin/feedback").header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(marker);
    }

    @Test
    void oneAddressCanSendOnlyAFewMessagesPerHour() throws Exception {
        for (int i = 0; i < 5; i++) {
            send("""
                    {"kind": "OTHER", "message": "Message number %d"}
                    """.formatted(i))
                    .andExpect(status().isCreated());
        }
        send("""
                {"kind": "OTHER", "message": "One too many"}
                """)
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions send(String json) throws Exception {
        return mockMvc.perform(post("/api/feedback").header("X-Forwarded-For", clientAddress)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
