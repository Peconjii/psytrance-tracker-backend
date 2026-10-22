package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A new account gets a link by email; opening it confirms the email. */
class EmailVerificationIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "secret-password-123";

    @Test
    void theEmailedLinkVerifiesTheAccountOnce() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        String token = login(username, PASSWORD);
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.emailVerified").value(false));

        String link = sentLink(username);
        String rawToken = link.substring(link.indexOf("token=") + "token=".length());
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.emailVerified").value(true));

        // A link works only once
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(rawToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherLinkWaitsAFewMinutes() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        // The link from the registration was just sent
        mockMvc.perform(post("/api/auth/resend-verification").header("Authorization", bearer(login(username, PASSWORD))))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(post("/api/auth/resend-verification"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownLinkIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"not-a-real-token\"}"))
                .andExpect(status().isBadRequest());
    }

    private String sentLink(String username) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(verificationMailer).sendVerificationLink(argThat((User user) -> user.getUsername().equals(username)),
                link.capture(), any(Duration.class));
        return link.getValue();
    }
}
