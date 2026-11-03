package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.client.GoogleIdTokens.GoogleAccount;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Sign in with Google", with the token check faked: which account it logs into, and making a new one */
class GoogleSignInIntegrationTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void googleSignInIsOn() {
        when(googleIdTokens.isEnabled()).thenReturn(true);
    }

    /** A Google account with an email used nowhere else, accepted for the credential "good-<its id>" */
    private GoogleAccount googleAccount(String name) {
        String id = UUID.randomUUID().toString();
        GoogleAccount account = new GoogleAccount(id, "g" + id.substring(0, 8) + "@gmail.com", name);
        when(googleIdTokens.verify("good-" + id)).thenReturn(Optional.of(account));
        return account;
    }

    private ResultActions signIn(GoogleAccount account, String username) throws Exception {
        String usernameJson = username == null ? "" : ", \"username\": \"%s\"".formatted(username);
        return mockMvc.perform(post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\": \"good-%s\"%s}".formatted(account.id(), usernameJson)));
    }

    private String usernameOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.username");
    }

    @Test
    void aNewGoogleAccountChoosesAUsernameAndThenSignsStraightIn() throws Exception {
        GoogleAccount google = googleAccount("Astrix Fan");

        signIn(google, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsUsername").value(true))
                .andExpect(jsonPath("$.suggestedUsername").value(startsWith("astrix_fan")))
                .andExpect(jsonPath("$.email").value(google.email()))
                .andExpect(jsonPath("$.token").doesNotExist());

        String username = uniqueUsername();
        String token = JsonPath.read(signIn(google, username).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");
        assertThat(usernameOf(token)).isEqualTo(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmail()).isEqualTo(google.email());

        // Next time: no username needed
        String again = JsonPath.read(signIn(google, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");
        assertThat(usernameOf(again)).isEqualTo(username);
    }

    @Test
    void anAccountWithTheSameConfirmedEmailIsTheOneSignedInto() throws Exception {
        GoogleAccount google = googleAccount("Someone");
        String username = uniqueUsername();
        register(username, "password123");
        User existing = userRepository.findByUsername(username).orElseThrow();
        existing.setEmail(google.email().toUpperCase());
        existing.setEmailVerified(true);
        userRepository.save(existing);

        String token = JsonPath.read(signIn(google, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        assertThat(usernameOf(token)).isEqualTo(username);
        assertThat(userRepository.findByUsername(username).orElseThrow().getGoogleId()).isEqualTo(google.id());
        // The password still works too
        login(username, "password123");
    }

    @Test
    void anAccountWhoseEmailNobodyConfirmedIsNotTakenOver() throws Exception {
        // Someone registered with this email without owning it; the real owner signs in with Google
        GoogleAccount google = googleAccount("Owner");
        String squatter = uniqueUsername();
        register(squatter, "password123");
        User unconfirmed = userRepository.findByUsername(squatter).orElseThrow();
        unconfirmed.setEmail(google.email());
        userRepository.save(unconfirmed);

        signIn(google, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsUsername").value(true));
        assertThat(userRepository.findByUsername(squatter).orElseThrow().getGoogleId()).isNull();
    }

    @Test
    void aTakenUsernameIsAConflict() throws Exception {
        String taken = uniqueUsername();
        register(taken, "password123");

        signIn(googleAccount("New"), taken).andExpect(status().isConflict());
    }

    @Test
    void aTokenGoogleDidNotMakeIsRefused() throws Exception {
        when(googleIdTokens.verify(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\": \"forged\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theFrontendGetsTheClientIdForItsButtonWithoutLoggingIn() throws Exception {
        when(googleIdTokens.clientId()).thenReturn("test-client.apps.googleusercontent.com");

        mockMvc.perform(get("/api/auth/google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("test-client.apps.googleusercontent.com"));
    }

    @Test
    void withoutAClientIdGoogleSignInIsOff() throws Exception {
        when(googleIdTokens.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/auth/google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(""));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\": \"anything\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
