package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public forms keep bots out without a captcha: a hidden field only bots fill in, and a few sends per day */
class FormGuardIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private UserRepository userRepository;

    @Test
    void botsFillingTheHiddenFieldGetNoAccountClaimOrPage() throws Exception {
        String username = uniqueUsername();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "email": "%s@example.com", "password": "%s", "website": "http://spam.example"}
                        """.formatted(username, username, PASSWORD)))
                .andExpect(status().isBadRequest());

        String token = verifiedUser();
        mockMvc.perform(post("/api/claims").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"FESTIVAL\", \"target\": \"ozora\", \"website\": \"http://spam.example\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"LABEL\", \"name\": \"Spam Records\", \"website\": \"http://spam.example\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneAccountAsksForAFewNewPagesADay() throws Exception {
        String token = verifiedUser();
        String unique = uniqueLetters();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"kind\": \"LABEL\", \"name\": \"Label %s %s\"}".formatted(unique, (char) ('a' + i))))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"LABEL\", \"name\": \"Label %s z\"}".formatted(unique)))
                .andExpect(status().isTooManyRequests());
    }

    private String verifiedUser() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        var user = userRepository.findByUsername(username).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        return login(username, PASSWORD);
    }
}
